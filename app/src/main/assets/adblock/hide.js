(function() {
  'use strict';

  /* ========== CONFIG ========== */
  var cfg = window._fbc || {};
  var hideAds     = cfg.adblock       !== false;
  var hideStories = cfg.block_stories !== false;
  var hideStatus  = cfg.block_status  !== false;
  var hidePymk    = cfg.block_pymk    !== false;

  // Multilingual ad-label dictionary (keys pre-normalized: lowercase,
  // whitespace/separators stripped). Extend freely.
  var AD_LABELS = {};
  ['ad','ads','sponsored','suggestedpost','paidpartnership',      // EN
   'anzeige','gesponsert',                                        // DE
   'publicité','sponsorisé',                                      // FR
   'publicidad','patrocinado',                                    // ES
   'pubblicità','sponsorizzato',                                  // IT
   'advertentie',                                                 // NL
   'reklama','реклама','reklam',                                  // PL/RU/TR
   '広告','广告','광고','quảngcáo','إعلان','مموّل'                 // CJK/VI/AR
  ].forEach(function(k){ AD_LABELS[k] = true; });

  var PYMK_LABELS = {};
  ['peopleyoumayknow','suggestforYou'.toLowerCase(),'suggestedforyou',
   'vorschlägefürdich','suggerimenti per te'.replace(/\s/g,'')
  ].forEach(function(k){ PYMK_LABELS[k] = true; });

  var MAX_NODES_PER_SCAN = 15000;   // hard CPU budget

  /* ========== NORMALIZATION (kills obfuscation) ========== */
  function norm(s) {
    return String(s || '')
      .toLowerCase()
      .replace(/[\u200B-\u200F\u2060\uFEFF\u180E]/g, '')  // zero-width tricks
      .replace(/[\s\u00B7\u2022\u2027\u30FB\u2219\u22C5\-–—_]+/g, ''); // sep junk
  }

  function isAdLabelText(rawText) {
    var t = norm(rawText);
    if (!t || t.length > 16) return false;         // long strings ≠ labels
    if (AD_LABELS[t]) return true;
    // tolerate one extra trailing char, e.g. "Ad*" or "Sponsored."
    return t.length <= 11 && AD_LABELS[t.slice(0, -1)];
  }

  /* ========== VISIBILITY (cached, no IE cruft) ========== */
  var HAS_CHECKVIS = typeof Element !== 'undefined' &&
                     Element.prototype.checkVisibility;

  function isVisible(el) {
    if (HAS_CHECKVIS) {
      try { return el.checkVisibility(); } catch(e) {}
    }
    var cs = window.getComputedStyle(el);
    return cs.display !== 'none' &&
           cs.visibility !== 'hidden' &&
           cs.opacity !== '0';
  }

  /* ========== HIDING via data attribute (idempotent, debuggable) ========== */
  function hideEl(el) {
    if (!el || el === document.body || el === document.documentElement) return;
    el.setAttribute('data-fbc-hidden', '');
  }

  /* ========== STRUCTURE HELPERS ========== */
  function getFeed() {
    return document.querySelector(
      'div[role="feed"],[data-pagelet="MainFeed"],[data-testid="newsFeed"]');
  }

  // climb to the direct child of `feed`, or nearest [role=article] fallback
  function getUnit(el, feed) {
    var p = el;
    while (p && p.parentElement) {
      if (p.parentElement === feed) return p;
      if (p.parentElement === document.body) break;
      if (p.getAttribute && p.getAttribute('role') === 'article' && feed)
        return p;                                    // non-feed contexts (video, reels)
      p = p.parentElement;
    }
    return null;
  }

  // strong server-side tell: "Sponsored"/label links point into /ads/
  function hasAdHref(unit) {
    var links = unit.querySelectorAll('a[href]');
    for (var i = 0; i < Math.min(links.length, 12); i++) {
      var h = links[i].getAttribute('href');
      if (h && (h.indexOf('/ads/about') === 0 ||
                h.indexOf('/ads/') !== -1 ||
                h.indexOf('ad_preferences') !== -1)) return true;
    }
    return false;
  }

  /* ========== SCANNER ========== */
  var activeWalkRoot = null;

  function walk(node, feed, budget) {
    while (node) {
      if (++budget.used > MAX_NODES_PER_SCAN) return null;

      if (node.nodeType === 3) {                      // TEXT NODE
        if (isAdLabelText(node.textContent)) {
          var p = node.parentElement;
          if (p && isVisible(p)) return p;
        }
      } else if (node.nodeType === 1) {
        if (!isVisible(node)) {                        // prune whole subtree
          node = nextSkippable(node, feed);
          continue;
        }
        var aria = node.getAttribute && node.getAttribute('aria-label');
        if (aria && isAdLabelText(aria)) return node;
        if (node.firstChild) return walk(node.firstChild, feed, budget);
      }
      node = nextNode(node, feed);
    }
    return null;
  }

  // iterative sibling climb — no deep recursion, aborts cleanly on match
  function nextNode(node, feed) {
    if (node.nextSibling) return node.nextSibling;
    var p = node.parentNode;
    while (p && p !== activeWalkRoot) {
      if (p.nextSibling) return p.nextSibling;
      p = p.parentNode;
    }
    return null;
  }

  function scanFeed() {
    if (!hideAds || !document.body) return;
    var feed = getFeed();

    var roots = [];
    if (feed) {
      roots.push(feed);
    } else {
