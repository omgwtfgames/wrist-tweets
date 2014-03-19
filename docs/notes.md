Wrist Tweets development notes
==============================

"Wrist Tweets on Google Play":https://play.google.com/store/apps/details?id=com.sonyericsson.extras.liveview.plugins.wristtweets

"Wrist Tweets blog posts":http://omgwtfgames.com/category/wrist-tweets-plugin/ at omgwtfgames.com

Development links
-----------------

* "Twitter API":http://dev.twitter.com/doc
* The "JTwitter library":http://www.winterwell.com/software/jtwitter.php used in this app.
** More complete JTwitter OAuth on Android example: http://rajujadhav22.wordpress.com/2010/11/16/post-tweet/
* StatusNet's "identi.ca Twitter-compatible API":http://status.net/wiki/TwitterCompatibleAPI

### Java regex for extracting URLs

* I used this: http://blog.houen.net/java-get-url-from-string/
* The regex used here may be slightly better: http://stackoverflow.com/questions/1806017/extracting-urls-from-a-text-document-using-java-regular-expressions

### Opening Tweets in Android Twitter clients

To figure out which intents various Twitter clients could accept, I used "Package Explorer":https://market.android.com/details?id=org.andr.pkgexp and the "Android Intent Playground":https://market.android.com/details?id=com.codtech.android.intentplayground .
I hadn't found the "these iSEC Partners tools":https://www.isecpartners.com/mobile-security-tools/ at the time, but the Manifest Explorer and Package Play apps look like they would also do the job.

When starting an activity with the Intent.ACTION_VIEW intent and a twitter.com url as data:

* official Twitter app
* Plume
appears in popup and opens these tweets as expected. 

* Hootsuite
* Twidroid Pro
appears in popup, but fails to open or opens with an error

* TweetDeck
* Seesmic
doesn't even appear in the popup, indicating it doesn't capture ACTION_VIEW intents containing a twitter.com url.

Intent filters of an app can be explored using the PackageExplorer app.

*TweetDeck* has intent filters for:

* Intent.ACTION_VIEW for urls like _tweetdeck://at/username_ which opens a user profile.
* Intent.ACTION_VIEW for urls like _tweetdeck://tag/some_tag_ which opens a column with _#some_tag_ as the hashtag.
* Intent.ACTION_VIEW for urls like _tweetdeck://tweetdeck.com_ which just opens tweetdeck.
* Intent.ACTION_SEND for text/plain and image/* mime types

*Twidroid Pro* has intent filters for:

* Intent.ACTION_VIEW for urls like _twidroid://message/bla_
* Intent.ACTION_VIEW for urls like _twitter://send/bla_
* Intent.ACTION_VIEW for urls like _twitter://com.twidroidpro.twidroidprofile/username_
* Intent.ACTION_VIEW for urls like _http://com.twidroid.list/list_
* Intent.ACTION_VIEW for urls like _twitter://com.twidroidpro.twittersearch/query_
* Intent.ACTION_VIEW to catch various URLs for third-party Twitter services (eg Twitlonger, Twitpic etc)
* Intent.ACTION_SEND for text/plain, text/twitter, application/twitter, image/png, image/jpeg and video/* mime types

*Hootsuite* uses the custom URI scheme _x-hoot-full://_ and has intent filters for:

* Intent.ACTION_VIEW for urls like _x-hoot-full://profile-twitter/username_
* Intent.ACTION_VIEW for urls like _x-hoot-full://search/query_
* Intent.ACTION_VIEW for urls like _x-hoot-full://list/list_name_
* Intent.ACTION_VIEW for urls like _x-hoot-full://web/http://some.url.com_
* Intent.ACTION_VIEW for urls like _x-hoot-full://profile-facebook/some_id_
* Intent.ACTION_VIEW for urls like _twitter://not_sure_what_this_part_is_
* Intent.ACTION_SEND for text/plain, application/twitter and image/* mime types
* Intent.ACTION_VIEW to catch twitter.com urls

The *Official Twitter app* has very few useful intent filters. They are:

* Intent.ACTION_VIEW for urls like _http://twitter.com/_ and _http://mobile.twitter.com/_ . Curiously, I can't find these in the AndroidManifest.xml data output by PackageExplorer, but they work in testing.
* According to PackageExplorer, the app has Intent.ACTION_VIEW intent filters for urls containing _http://mobile.twitter.com/sessions/client/main_ and _http://mobile.twitter.com/sessions/client_ .. in my testing that app doesn't seem to intercept these intents.
* Intent.ACTION_VIEW for urls at host _com.android.contacts_ with mimetype _vnd.android.cursor.item/vnd.twitter.profile_ .. untested .. might be useful for accessing a Twitter profile associated with the phones contacts.
* Intent.ACTION_VIEW for the scheme _twitter-android-app://_ which appears to for OAuth based login for that app only. Not useful for much.
* Intent.ACTION_SEND for text/plain and image/* mime types
