(function() {
  'use strict';

  var cfg = window._fbc || {};
  var hideAds     = cfg.adblock       !== false;
  var hideStories = cfg.block_stories !== false;
  var hideStatus  = cfg.block_status  !== false;
  var hidePymk    = cfg.block_pymk    !== false;

  var hiddenEls = [];

  function hideEl(el) {
    if (!el || el === document.body || el === document.documentElement) return;
    for (var i = 0; i < hiddenEls.length; i++) if (hiddenEls[i] === el) return;
    hiddenEls.push(el);
    el.style.cssText += ';display:none!important';
  }

  // CSS injection
  function injectCSS() {
    var s = document.getElementById('_fbc');
    if (!s) {
      s = document.createElement('style');
      s.id = '_fbc';
      var head = document.head || document.getElementsByTagName('head')[0] || document.documentElement;
      head.appendChild(s);
    }
    var css = '';
    if (hideStories) css += '[data-pagelet="Stories"]{display:none!important}[aria-label="Stories"]{display:none!important}';
    if (hideStatus)  css += '[data-pagelet="FeedComposer"]{display:none!important}';
    s.textContent = css;
  }

  // Hide open app buttons
  function hideOpenApp() {
    var els = document.querySelectorAll('a,button');
    for (var i = 0; i < els.length; i++) {
      var t = els[i].textContent.trim();
      if (t === 'Open app' || t === 'Open App') els[i].style.cssText += ';display:none!important';
    }
    if (document.body) document.body.style.paddingBottom = '0';
  }

  // Check if text is an ad label
  function isAdLabel(text) {
    var t = (text || '').trim();
    return t === 'Ad' || t === 'Sponsored' || t === 'Suggested Post' || t === 'Paid partnership';
  }

  // Walk all text nodes — most compatible approach, works on all WebView versions
  function findAdLabel(root) {
    var result = null;
    function walk(node) {
      if (result) return;
      if (node.nodeType === 3) { // text node
        if (isAdLabel(node.textContent)) {
          // Check parent is visible
          var p = node.parentElement;
          if (p) {
            var cs = p.currentStyle || window.getComputedStyle(p);
            if (cs.display !== 'none' && cs.visibility !== 'hidden') {
              result = p;
            }
          }
        }
        return;
      }
      if (node.nodeType !== 1) return;
      // Skip hidden nodes
      var cs = node.currentStyle || window.getComputedStyle(node);
      if (cs.display === 'none' || cs.visibility === 'hidden') return;
      for (var i = 0; i < node.childNodes.length; i++) walk(node.childNodes[i]);
    }
    walk(root);
    return result;
  }

  // Get direct child of feed containing element
  function getFeedChild(el, feed) {
    var p = el;
    while (p && p.parentElement) {
      if (p.parentElement === feed) return p;
      if (p.parentElement === document.body) return null;
      p = p.parentElement;
    }
    return null;
  }

  function scanFeed() {
    if (!hideAds || !document.body) return;

    // Try multiple feed selectors for compatibility
    var feed = document.querySelector('div[role="feed"]') ||
               document.querySelector('[data-pagelet="Feed"]') ||
               document.querySelector('[data-testid="Keycommand_wrapper_news_feed"]');
    if (!feed) return;

    var items = feed.children;
    for (var i = 0; i < items.length; i++) {
      var item = items[i];
      if (item.style.display === 'none') continue;

      // Method 1: aria-label
      if (item.querySelector('[aria-label="Sponsored"],[aria-label="Ad"]')) {
        hideEl(item); continue;
      }

      // Method 2: Walk text nodes
      var adEl = findAdLabel(item);
      if (adEl) { hideEl(item); continue; }

      // Method 3: innerHTML contains common ad patterns
      var html = item.innerHTML;
      if (html.indexOf('>Ad<') >= 0 || html.indexOf('>Sponsored<') >= 0 ||
          html.indexOf('">Ad"') >= 0 || html.indexOf('"Sponsored"') >= 0) {
        // Verify it's not a comment or quote
        var spans = item.querySelectorAll('span,a');
        for (var j = 0; j < spans.length; j++) {
          if (isAdLabel(spans[j].textContent)) {
            hideEl(item); break;
          }
        }
      }
    }

    // PYMK
    if (hidePymk) {
      var pymkList = ['People You May Know','People you may know','Suggested for You'];
      var allSpans = feed.querySelectorAll('span,h2,h3,h4');
      for (var k = 0; k < allSpans.length; k++) {
        var t = allSpans[k].textContent.trim();
        for (var m = 0; m < pymkList.length; m++) {
          if (t === pymkList[m]) {
            var fi = getFeedChild(allSpans[k], feed);
            if (fi) hideEl(fi);
            break;
          }
        }
      }
    }
  }

  function scanVideoAds() {
    if (!hideAds || !document.body) return;
    var spans = document.body.querySelectorAll('span,a');
    for (var i = 0; i < spans.length; i++) {
      var el = spans[i];
      if (el.childElementCount > 0) continue;
      if (!isAdLabel(el.textContent)) continue;
      var cs = el.currentStyle || window.getComputedStyle(el);
      if (cs.display === 'none' || cs.visibility === 'hidden') continue;
      // Walk up — hide small container only
      var p = el.parentElement;
      for (var j = 0; j < 10; j++) {
        if (!p || p === document.body) break;
        var role = p.getAttribute ? p.getAttribute('role') : null;
        if (role === 'feed') break;
        var h = p.offsetHeight; var w = p.offsetWidth;
        if (w > window.innerWidth * 0.4 && h > 30 && h < window.innerHeight * 0.5) {
          hideEl(p); break;
        }
        p = p.parentElement;
      }
    }
  }

  function watchFeed() {
    var feed = document.querySelector('div[role="feed"]');
    if (!feed || feed._w) return !!feed;
    feed._w = true;
    if (window.MutationObserver) {
      var t;
      new MutationObserver(function() {
        clearTimeout(t); t = setTimeout(scanFeed, 500);
      }).observe(feed, {childList: true});
    }
    return true;
  }

  function run() {
    injectCSS();
    hideOpenApp();
    scanFeed();
    scanVideoAds();
  }

  run();
  if (document.addEventListener) {
    document.addEventListener('DOMContentLoaded', run);
  }

  var lastUrl = window.location.href;
  var poll = setInterval(function() {
    var cur = window.location.href;
    if (cur !== lastUrl) {
      lastUrl = cur;
      setTimeout(scanVideoAds, 400);
      setTimeout(scanVideoAds, 1000);
    }
    run();
    watchFeed();
  }, 1000);

  // Stop polling after 30s to save battery
  setTimeout(function() { clearInterval(poll); }, 30000);

  // Keep running for infinite scroll
  setInterval(run, 5000);

})();
