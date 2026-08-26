// Enhanced hide.js with Infinix/XOS optimization
(function() {
  'use strict';

  // ========== DEVICE DETECTION & CONFIGURATION ==========
  var userAgent = navigator.userAgent;
  var isInfinixXOS = /XOS|Infinix/i.test(userAgent);
  var isLowEndDevice = isInfinixXOS || /Android [4-6]\./i.test(userAgent) || 
                      navigator.hardwareConcurrency <= 4;
  
  // Adaptive scan frequencies based on device capability
  var SCAN_FREQUENCIES = {
    highEnd: {
      urlChange: 1000,
      fullScan: 500,
      quickScan: 200
    },
    midRange: {
      urlChange: 1500,
      fullScan: 800,
      quickScan: 400
    },
    lowEnd: {
      urlChange: 2500,
      fullScan: 1200,
      quickScan: 600
    }
  };

  var config = isLowEndDevice ? SCAN_FREQUENCIES.lowEnd : 
              isInfinixXOS ? SCAN_FREQUENCIES.midRange : 
              SCAN_FREQUENCIES.highEnd;

  // Original configuration
  var cfg = window._fbc || {};
  var hideAds     = cfg.adblock       !== false;
  var hideStories = cfg.block_stories !== false;
  var hideStatus  = cfg.block_status  !== false;
  var hidePymk    = cfg.block_pymk    !== false;

  var hiddenEls = [];
  var lastScanTime = 0;
  var observerActive = false;

  // Improved visibility checker for older WebViews
  function isVisible(el) {
    if (!el || el === document.body) return true;
    
    // Check for common hiding techniques
    if (el.style.display === 'none' || el.style.visibility === 'hidden') return false;
    
    // Modern approach with fallback
    try {
      var cs = window.getComputedStyle(el);
      return cs.display !== 'none' && cs.visibility !== 'hidden' && cs.opacity !== '0';
    } catch (e) {
      // Fallback for very old WebView versions
      return el.offsetWidth > 0 && el.offsetHeight > 0;
    }
  }

  function hideEl(el) {
    if (!el || el === document.body || el === document.documentElement) return;
    
    // Prevent duplicate processing
    if (hiddenEls.indexOf(el) !== -1) return;
    hiddenEls.push(el);
    
    // Multiple hiding methods for maximum compatibility
    try {
      el.style.setProperty('display', 'none', 'important');
      el.setAttribute('data-fbc-hidden', 'true');
    } catch (e) {
      // Very old WebView fallback
      el.style.display = 'none';
      el.style.visibility = 'hidden';
    }
  }

  // Enhanced CSS injection with progressive enhancement
  function injectCSS() {
    var s = document.getElementById('_fbc_css');
    if (!s) {
      s = document.createElement('style');
      s.id = '_fbc_css';
      document.head.appendChild(s);
    }
    
    var cssRules = [
      // Base hiding rules with fallbacks
      '.fbc-hidden { display: none !important; visibility: hidden !important; opacity: 0 !important; }',
      '[data-fbc-hidden] { display: none !important; }',
      ''
    ];
    
    if (hideStories) {
      cssRules.push('[data-pagelet="Stories"] { display: none !important; }');
      cssRules.push('[aria-label="Stories"] { display: none !important; }');
    }
    
    if (hideStatus) {
      cssRules.push('[data-pagelet="FeedComposer"] { display: none !important; }');
    }
    
    s.textContent = cssRules.join('\n');
  }

  // Enhanced ad label detection with multiple strategies
  function isAdLabel(text) {
    if (!text) return false;
    
    var t = text.toLowerCase().trim();
    var adPatterns = [
      'sponsored',
      'ad',
      'advertisement',
      'paid partnership',
      'suggested post',
      'promoted',
      'partner'
    ];
    
    // Direct match
    for (var i = 0; i < adPatterns.length; i++) {
      if (t === adPatterns[i]) return true;
    }
    
    // Contains match for obfuscated text
    if (t.includes(adPatterns[0]) || t.includes(adPatterns[1])) {
      // Additional check to avoid false positives
      var words = t.split(/\s+/);
      if (words.length <= 2) return true;
    }
    
    return false;
  }

  // Optimized TreeWalker with memory management
  function findAdLabel(root) {
    var result = null;
    var processedNodes = new Set(); // Memory leak prevention
    
    function walk(node) {
      if (result || processedNodes.has(node)) return;
      processedNodes.add(node);
      
      if (node.nodeType === Node.TEXT_NODE) {
        if (isAdLabel(node.textContent)) {
          var parent = node.parentElement;
          if (parent && isVisible(parent)) {
            result = parent;
          }
        }
        return;
      }
      
      if (node.nodeType === Node.ELEMENT_NODE && isVisible(node)) {
        for (var i = 0; i < node.childNodes.length; i++) {
          walk(node.childNodes[i]);
        }
      }
    }
    
    walk(root);
    
    // Cleanup to prevent memory leaks
    processedNodes.clear();
    return result;
  }

  // Feed scanner with retry logic for Infinix
  function scanFeed() {
    if (!hideAds || !document.body) return;
    
    var currentTime = Date.now();
    if (currentTime - lastScanTime < 50) return; // Throttle scanning
    
    lastScanTime = currentTime;
    
    // Try multiple feed selectors for compatibility
    var feedSelectors = [
      'div[role="feed"]',
      '[data-pagelet="Feed"]',
      '[data-testid="Keycommand_wrapper_news_feed"]',
      '[role="main"] div[style*="overflow-y: auto"]'
    ];
    
    var feed = null;
    for (var i = 0; i < feedSelectors.length; i++) {
      feed = document.querySelector(feedSelectors[i]);
      if (feed) break;
    }
    
    if (!feed) {
      // Retry after delay for Infinix (slower DOM loading)
      setTimeout(scanFeed, 2000);
      return;
    }

    var items = feed.children;
    for (var i = 0; i < items.length; i++) {
      var item = items[i];
      if (item.hasAttribute('data-fbc-hidden')) continue;
      
      // Method 1: Quick aria-label check
      var adElement = item.querySelector('[aria-label="Sponsored"],[aria-label="Ad"]');
      if (adElement) {
        hideEl(item);
        continue;
      }
      
      // Method 2: Text node analysis (with depth limit)
      var textNodeDepth = 0;
      var foundAdNode = false;
      
      function traverseTextNodes(node) {
        if (foundAdNode || textNodeDepth > 5) return;
        
        if (node.nodeType === Node.TEXT_NODE && isAdLabel(node.textContent)) {
          var parent = node.parentElement;
          if (parent && isVisible(parent)) {
            hideEl(item);
            foundAdNode = true;
            return;
          }
        }
        
        for (var child = node.firstChild; child; child = child.nextSibling) {
          textNodeDepth++;
          traverseTextNodes(child);
          textNodeDepth--;
        }
      }
      
      traverseTextNodes(item);
      
      if (!foundAdNode) {
        // Method 3: Pattern matching in HTML
        var htmlContent = item.innerHTML;
        if (htmlContent && (
          htmlContent.indexOf('>Ad<') !== -1 ||
          htmlContent.indexOf('>Sponsored<') !== -1 ||
          htmlContent.indexOf('"Sponsored"') !== -1 ||
          htmlContent.indexOf('aria-hidden="false">Ad</span>') !== -1
        )) {
          // Additional verification to prevent false positives
          var spans = item.querySelectorAll('span, a, h3, h4');
          for (var j = 0; j < spans.length; j++) {
            if (isAdLabel(spans[j].textContent)) {
              hideEl(item);
              break;
            }
          }
        }
      }
    }

    // PYMK section with enhanced detection
    if (hidePymk) {
      var pymkPatterns = [
        'people you may know',
        'people you mayknow',
        'suggested for you',
        'recommended friends'
      ];
      
      var headers = feed.querySelectorAll('h2, h3, h4, [role="heading"]');
      for (var k = 0; k < headers.length; k++) {
        var headerText = headers[k].textContent.toLowerCase().trim();
        
        for (var pattern of pymkPatterns) {
          if (headerText.includes(pattern)) {
            // Find the parent container of this header
            var container = headers[k];
            for (var level = 0; level < 3; level++) {
              if (!container.parentElement) break;
              
              if (container.parentElement === feed) {
                hideEl(container);
                break;
              }
              container = container.parentElement;
            }
            break;
          }
        }
      }
    }
  }

  // Video ad detector optimized for Infinix
  function scanVideoAds() {
    if (!hideAds || !document.body) return;
    
    // Only scan video sections to reduce load
    var videoContainers = document.querySelectorAll(
      'video, [data-pagelet="video_home"], [data-pagelet="watch"]'
    );
    
    for (var i = 0; i < videoContainers.length; i++) {
      var container = videoContainers[i];
      var parentsToCheck = [];
      
      // Collect relevant ancestors
      var parent = container.parentElement;
      while (parent && parent !== document.body && parentsToCheck.length < 5) {
        parentsToCheck.push(parent);
        parent = parent.parentElement;
      }
      
      // Look for ad labels near video
      for (var j = 0; j < parentsToCheck.length; j++) {
        var checkParent = parentsToCheck[j];
        var spans = checkParent.querySelectorAll('span, a, div[role="button"]');
        
        for (var k = 0; k < spans.length; k++) {
          if (isAdLabel(spans[k].textContent)) {
            var parentSize = checkParent.getBoundingClientRect();
            
            // Hide container that seems like a video ad overlay
            if (parentSize.width > window.innerWidth * 0.3 && 
                parentSize.height > 40 && 
                parentSize.height < window.innerHeight * 0.7) {
              hideEl(checkParent);
              break;
            }
          }
        }
      }
    }
  }

  // Enhanced URL watcher with proper cleanup
  function watchUrlChanges() {
    var currentUrl = window.location.href;
    var urlWatcher = setInterval(function() {
      var newUrl = window.location.href;
      if (newUrl !== currentUrl) {
        currentUrl = newUrl;
        
        // Delayed scans for page transitions
        setTimeout(scanVideoAds, config.urlChange);
        setTimeout(scanVideoAds, config.urlChange * 2);
        setTimeout(scanFeed, config.urlChange);
      }
    }, config.quickScan);
    
    // Return cleanup function
    return function() {
      clearInterval(urlWatcher);
    };
  }

  // Initialize everything with error boundaries
  function initialize() {
    try {
      injectCSS();
      
      // Initial scan after short delay
      setTimeout(function() {
        try {
          scanFeed();
          scanVideoAds();
          hideOpenAppButtons();
        } catch (error) {
          console.warn('Initial scan error:', error);
        }
      }, 1000);
      
      // Set up URL watcher
      var cleanupUrlWatcher = watchUrlChanges();
      
      // Mutation observer for dynamic content
      if (window.MutationObserver) {
        var observerTimeout;
        var observer = new MutationObserver(function(mutations) {
          clearTimeout(observerTimeout);
          observerTimeout = setTimeout(function() {
            try {
              scanFeed();
              scanVideoAds();
            } catch (error) {
              console.warn('Observation scan error:', error);
            }
          }, config.fullScan);
        });
        
        var feedElement = document.querySelector('div[role="feed"]') || document.body;
        observer.observe(feedElement, {
          childList: true,
          subtree: true,
          attributes: false,
          characterData: false
        });
        
        observerActive = true;
      }
      
      // Return cleanup function
      return function() {
        clearInterval(cleanupUrlWatcher);
        if (observer && observer.disconnect) {
          observer.disconnect();
        }
      };
    } catch (error) {
      console.error('Initialization failed:', error);
      return function() {}; // Return no-op function
    }
  }

  // Open App button hider (enhanced)
  function hideOpenAppButtons() {
    var buttons = document.querySelectorAll('a[href*="fbclid"], button, [role="button"]');
    for (var i = 0; i < buttons.length; i++) {
      var buttonText = buttons[i].textContent.trim().toLowerCase();
      if (buttonText.includes('open app') || buttonText.includes('open the app')) {
        hideEl(buttons[i]);
      }
    }
    
    // Remove padding adjustments
    document.body.style.paddingBottom = '0';
  }

  // Auto-adaptive scheduler that reduces load over time
  function createScheduler() {
    var initialRun = true;
    var lastRunTime = Date.now();
    var adaptiveDelay = 5000;
    
    function scheduleNext() {
      var now = Date.now();
      var timeSinceLastRun = now - lastRunTime;
      
      // Adaptive algorithm: reduce frequency over time
      if (timeSinceLastRun > 60000) { // After 1 minute
        adaptiveDelay = Math.min(adaptiveDelay * 1.5, 30000); // Increase gradually
      } else if (timeSinceLastRun < 5000) { // Very recent run
        adaptiveDelay = Math.max(adaptiveDelay * 0.9, 1000); // Decrease slightly
      }
      
      setTimeout(function() {
        try {
          // Perform maintenance scans
          if (initialRun) {
            scanFeed();
            scanVideoAds();
            hideOpenAppButtons();
            initialRun = false;
          } else {
            // Light scan for continuous operation
            scanVideoAds();
            if (Math.random() > 0.7) { // Occasional full scan
              scanFeed();
            }
          }
          
          lastRunTime = Date.now();
          scheduleNext();
        } catch (error) {
          console.warn('Scheduled task error:', error);
          // Continue despite errors
          lastRunTime = Date.now();
          scheduleNext();
        }
      }, adaptiveDelay);
    }
    
    // Start the scheduler
    scheduleNext();
  }

  // Run initialization when ready
  function startWhenReady() {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', function() {
        var cleanup = initialize();
        createScheduler();
        
        // Cleanup on page unload
        window.addEventListener('beforeunload', cleanup);
      });
    } else {
      var cleanup = initialize();
      createScheduler();
      window.addEventListener('beforeunload', cleanup);
    }
  }

  // Start immediately
  startWhenReady();
})();
