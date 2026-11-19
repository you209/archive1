# Bargain Bloom v0.3 — Android APK

Native Android clothing deal finder. Scans publicly accessible Shopify-style product feeds (where they work), stores real prices and retailer photos, and links to official sale pages for shops without supported feeds.

Features: size and colour filters, clothing category filters, favourites and saved style collections, 7-day first-tracked and recorded price-drop feeds, price history, target-price alerts via Android WorkManager, personalised favourites ranking, budget outfit combinations, an on-device experimental photo colour/pattern matcher, editable sizing preferences, and user-configured Tasmania shipping estimates.

**Limitations**: Kmart, BIG W, Bras N Things, Bonds, Cotton On, Harris Scarfe, Target, Sportsgirl and many others are browse-only. Bendon, Boody, Nala, Love Luna and the original feed shops are best-effort feeds, not verified live integrations. Product prices are not checkout guarantees; custom shipping values are estimates only. Photo search is colour/pattern similarity, not AI garment detection. Background tasks run approximately every 12 hours as Android permits.

Build: Gradle 8.10.2, Android 35, Java 17. GitHub Actions generates debug APK artifact on the isolated feature branch.