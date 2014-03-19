/*
 * Copyright (c) 2011 Andrew Perry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * 
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.sonyericsson.extras.liveview.plugins.wristtweets;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import winterwell.jtwitter.OAuthSignpostClient;
import winterwell.jtwitter.Twitter;
import winterwell.jtwitter.TwitterList;
import winterwell.jtwitter.Twitter.KEntityType;
import winterwell.jtwitter.Message;
import winterwell.jtwitter.Status;
import winterwell.jtwitter.Twitter.TweetEntity;

import com.sonyericsson.extras.liveview.plugins.AbstractPluginService;
import com.sonyericsson.extras.liveview.plugins.PluginConstants;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

/**
 * Wrist Tweet service. Check Twitter, sends notifications to LiveView microdisplay.
 */
public class WristTweetsService extends AbstractPluginService {
	private static String LINK_URL_BASE = "https://twitter.com/";
	private String API_URL = "https://api.twitter.com/1.1";
	
	// identi.ca works with the Twitter compatible API,
	// ( http://status.net/wiki/TwitterCompatibleAPI )
	// however Twitter-style lists are not implemented
	// so we need to add some identica specific stuff to ignore
	// lists and just get regular timeline updates.
	// We could try to support identica 'groups' by
	// extending TwitterJ - they can be got as per a
	// normal timeline but at /statusnet/groups/timeline
	//private static String LINK_URL_BASE = "http://identi.ca/";
	//private String API_URL = "http://identi.ca/api/";
	
    // Our handler.
    private Handler mHandler = null;
    
    // Is loop running?
    private boolean mWorkerRunning = false;
    
    // Preferences - update interval
    private static final String UPDATE_INTERVAL = "updateInterval";
    private static final String USERNAME = "username";
    private static final String LIST = "list";
    private static final int ACTION_MODE_OPEN_TWITTER_COM = 0;
    private static final int ACTION_MODE_OPEN_FIRST_LINK = 1;
    private static final int ACTION_MODE_OPEN_TWEETDECK_PROFILE = 2;
    private static final int ACTION_MODE_OPEN_TWIDROIDPRO_PROFILE = 3;
    private static final int ACTION_MODE_OPEN_HOOTSUITE_PROFILE = 4;
    
    private long mUpdateInterval = 300000;
    private String mUsername = "";
    private String mList = "";
    private int mMaxTweets = 5;
    
    private String TWITTER_OAUTH_ACCESS_TOKEN = "";
    private String TWITTER_OAUTH_ACCESS_TOKEN_SECRET = "";
    
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);

        // Create handler.
        if(mHandler == null) {
            mHandler = new Handler();
        }
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		// ... 
		// Do plugin specifics.
		// ...
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();	
		// ... 
		// Do plugin specifics.
		// ...
	}
	
    /**
     * Plugin is just sending notifications.
     */
    protected boolean isSandboxPlugin() {
        return false;
    }
	
	/**
	 * Must be implemented. Starts plugin work, if any.
	 */
	protected void startWork() {
		// Check if plugin is enabled.
		if(!mWorkerRunning && mSharedPreferences.getBoolean(PluginConstants.PREFERENCES_PLUGIN_ENABLED, false)) {
			readSharedPreferences();
			mWorkerRunning = true;
			scheduleTimer();
		}
	}
	
	/**
	 * Must be implemented. Stops plugin work, if any.
	 */
	protected void stopWork() {
	    mHandler.removeCallbacks(mAnnouncer);
	    mWorkerRunning = false;
	}
	
	/**
	 * Must be implemented.
	 * 
	 * PluginService has done connection and registering to the LiveView Service. 
	 * 
	 * If needed, do additional actions here, e.g. 
	 * starting any worker that is needed.
	 */
	protected void onServiceConnectedExtended(ComponentName className, IBinder service) {
	
	}
	
	/**
	 * Must be implemented.
	 * 
	 * PluginService has done disconnection from LiveView and service has been stopped. 
	 * 
	 * Do any additional actions here.
	 */
	protected void onServiceDisconnectedExtended(ComponentName className) {
		
	}

	/**
	 * Must be implemented.
	 * 
	 * PluginService has checked if plugin has been enabled/disabled.
	 * 
	 * The shared preferences has been changed. Take actions needed. 
	 */	
	protected void onSharedPreferenceChangedExtended(SharedPreferences pref, String key) {
		if(key.equals(UPDATE_INTERVAL)) {
			long value = Long.parseLong(pref.getString(UPDATE_INTERVAL, "300"));
			mUpdateInterval = value * 1000;
			doUpdateNow();
			Log.d(PluginConstants.LOG_TAG, "Preferences changed - update interval: " + mUpdateInterval);
		}
		if(key.equals(USERNAME)) {
			mUsername = pref.getString("username", "pansapiens");
			doUpdateNow();
			Log.d(PluginConstants.LOG_TAG, "Preferences changed - username: " + mUsername);
		}
		if(key.equals(LIST)) {
			mList = pref.getString("list", "");
			doUpdateNow();
			Log.d(PluginConstants.LOG_TAG, "Preferences changed - list: " + mList);
			
		}
	}

	/*
	 *  Runs a Twitter check and notification update job immediately.
	 */
	protected void doUpdateNow() {		
		// update automatically upon preference change
        if(mSharedPreferences.getBoolean(PluginConstants.PREFERENCES_PLUGIN_ENABLED, false)) {
        	
        	Log.d(PluginConstants.LOG_TAG, "Doing immediate update.");
        	
        	readSharedPreferences();
    		
        	// remove any pending updates then add one that
    		// will execute 1 sec in the future
        	mHandler.removeCallbacks(mAnnouncer);
        	
            mHandler.postDelayed(mAnnouncer, 1);
        }
	}
	
	protected void startPlugin() {
		Log.d(PluginConstants.LOG_TAG, "startPlugin");
		
		// so that update happens upon reconnect, sometimes
		doUpdateNow();
		mWorkerRunning = true;
		
		//startWork();
	}
			
	protected void stopPlugin() {
		Log.d(PluginConstants.LOG_TAG, "stopPlugin");
		stopWork();
	}

	protected void button(String buttonType, boolean doublepress, boolean longpress) {
        Log.d(PluginConstants.LOG_TAG, "button - type " + buttonType + ", doublepress " + doublepress + ", longpress " + longpress);
    }

	protected void displayCaps(int displayWidthPx, int displayHeigthPx) {
		
		// FIXME: A HACK so that update happens upon reconnect
		// couldn't figure out how to hook into anywhere else
		// (best place would be PluginReciever.onReceive "start" command)
		//if (mSharedPreferences.getBoolean("update_on_reconnect", true) && displayWidthPx == 128) {
		if (displayWidthPx == 128) {
			doUpdateNow();
		}
		
        Log.d(PluginConstants.LOG_TAG, "displayCaps - width " + displayWidthPx + ", height " + displayHeigthPx);
    }

	protected void onUnregistered() {
		Log.d(PluginConstants.LOG_TAG, "onUnregistered");
		stopWork();
	}

	protected void openInPhone(String openInPhoneAction) {
		Log.d(PluginConstants.LOG_TAG, "openInPhone: " + openInPhoneAction);
		Log.d(PluginConstants.LOG_TAG, "Opening on URL on phone: " + openInPhoneAction);
		
		// Open a URL (in browser or any other client that catches the intent)
		final Uri uri = Uri.parse(openInPhoneAction);
		final Intent browserIntent = new Intent(Intent.ACTION_VIEW);
		browserIntent.setData(uri);
		browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			startActivity(browserIntent);
		} catch (ActivityNotFoundException e) {
			Log.d(PluginConstants.LOG_TAG, "Cannot open URL: " + openInPhoneAction);
		}
		
	}
	
	protected void screenMode(int mode) {
	    Log.d(PluginConstants.LOG_TAG, "screenMode: screen is now " + ((mode == 0) ? "OFF" : "ON"));
	}
			
	private void sendAnnounce(String header, String body, long timestamp, String url) {
		try {
			if(mWorkerRunning && (mLiveViewAdapter != null) && mSharedPreferences.getBoolean(PluginConstants.PREFERENCES_PLUGIN_ENABLED, false)) {
			    mLiveViewAdapter.sendAnnounce(mPluginId, mMenuIcon, header, body, timestamp, url);
				Log.d(PluginConstants.LOG_TAG, "Announce sent to LiveView");
			} else {
				Log.d(PluginConstants.LOG_TAG, "LiveView not reachable");
			}
		} catch(Exception e) {
			Log.e(PluginConstants.LOG_TAG, "Failed to send announce", e);
		}
	}
	
    /**
     * Schedules a timer. 
     * Takes a delay in milliseconds for when the timer will trigger.
     */
    private void scheduleTimer(long delay) {
        if(mWorkerRunning) {
        	
        	// remove any pending updates, ie if we have just rescheduled
        	// due to changing the update time
        	mHandler.removeCallbacks(mAnnouncer);
        	
            mHandler.postDelayed(mAnnouncer, delay);
            Log.d(PluginConstants.LOG_TAG, "Scheduled next update at : " + epoch2date((System.currentTimeMillis()+mUpdateInterval)) );
        }
    }
    
    /**
     *  Schedules a timer that will trigger in mUpdateInterval milliseconds time.
     */
    private void scheduleTimer() {
    	scheduleTimer(mUpdateInterval);
    }
	
    /**
     * Sets various instance variables from those stored in SharedPreferences.
     */
    private void readSharedPreferences() {
		long value = Long.parseLong(mSharedPreferences.getString(UPDATE_INTERVAL, "300"));
		mUpdateInterval = value * 1000;
		mUsername = mSharedPreferences.getString("username", "OMGWTFGAMES");
		mList = mSharedPreferences.getString("list", "");
		mMaxTweets = Integer.parseInt(mSharedPreferences.getString("max_tweets", "5"));
		
		TWITTER_OAUTH_ACCESS_TOKEN = mSharedPreferences.getString("TWITTER_OAUTH_ACCESS_TOKEN", "");
		TWITTER_OAUTH_ACCESS_TOKEN_SECRET = mSharedPreferences.getString("TWITTER_OAUTH_ACCESS_TOKEN_SECRET", "");
		
    }
    
	/**
	* Finds urls in the tweet via entities. 
	* if there is only one, that will be the link followed
	* when the LiveView action button is pressed. otherwise, the url
	* will be the Twitter url of the tweet
	* 
	* Not currently working reliably, since tweets don't always
	* have URLs encoded as entities (it's a opt-in feature, as of Feb 2010).
	*/
    private String getFirstURLFromTweet(Status t) {
    	String url = "";
    	List<TweetEntity> entities = null;
    	ArrayList<String> links = pullLinks(t.getText());
		entities = t.getTweetEntities(KEntityType.urls);
		if (entities != null && entities.size() == 1) {
			url = entities.get(0).toString();
		} else if (links.size() != 0) {
			return links.get(0);
		} else {
			url = LINK_URL_BASE+t.getUser().getScreenName()+"/status/"+t.getId();
		}

		return url;
    }
    
    // takes a value in milliseconds since epoch
    // returns a standard date string
    String epoch2date(long t) {
    	Date d = new Date();
    	d.setTime(t);
    	return d.toString();
    }
        
    // Pull all links from the body for easy retrieval
    // http://blog.houen.net/java-get-url-from-string/
    private ArrayList<String> pullLinks(String text) {
    	ArrayList<String> links = new ArrayList<String>();
    	 
    	String regex = "\\(?\\b(http://|www[.])[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|]";
    	//String url_re = "(?i)\\b((?:[a-z][\\w-]+:(?:/{1,3}|[a-z0-9%])|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))";
    	//String url_re = "\\b(([\\w-]+://?|www[.])[^\\s()<>]+(?:\\([\\w\\d]+\\)|([^[:punct:]\\s]|/)))"; // uses [:punct:] .. not Java compatible ?
    	//String url_re = "\\b(([\\w-]+://?|www[.])[^\\s()<>]+(?:\\([\\w\\d]+\\)|([^[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]\\s]|/)))"; // doesn't use [:punct:]
    	//String url_re = "\\b(https?|ftp|file)://[-A-Z0-9+&@#/%?=~_|!:,.;]*[-A-Z0-9+&@#/%=~_|]"; // also allows http, ftp and file protocols
    	
    	Pattern p = Pattern.compile(regex);
    	Matcher m = p.matcher(text);
    	while(m.find()) {
    		String urlStr = m.group();
    		if (urlStr.startsWith("(") && urlStr.endsWith(")")) {
    			urlStr = urlStr.substring(1, urlStr.length() - 1);
    		}
    		links.add(urlStr);
    	}
    	return links;
    }
    
    // http://stackoverflow.com/questions/1560788/how-to-check-internet-access-on-android-inetaddress-never-timeouts/2001824#2001824
    public boolean isOnline() {
    	 ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    	 return cm.getActiveNetworkInfo() != null && 
  		       cm.getActiveNetworkInfo().isConnectedOrConnecting();
    }
    
    /**
     * The runnable used for posting to handler
     */
    private Runnable mAnnouncer = new Runnable() {
        
    	/**
    	 * Takes a list of tweets and the oldest id to notify,
    	 * sends notification of newer tweets to LiveView.
    	 * 
    	 * Returns id of the newest tweet sent.
    	 */
    	private BigInteger notifyTweets(ArrayList<Status> tweets, BigInteger last_id) {	
        	
    		int mActionButtonMode = Integer.parseInt(mSharedPreferences.getString("action_button_mode", "0"));
    		// send notification of any new tweets & messages
        	BigInteger t_id;
        	BigInteger new_last_id = new BigInteger("0");
        	int sent_count = 0;
        	for (Status t : tweets) {
        		t_id = t.getId();
        		if ( (t_id.compareTo(last_id) == 1) && (sent_count < mMaxTweets) ) {  			            			
        			
        			String url;
        			switch (mActionButtonMode) {
        			case ACTION_MODE_OPEN_TWITTER_COM:
        				url = LINK_URL_BASE+t.getUser().getScreenName()+"/status/"+t.getId();
        				break;
        			case ACTION_MODE_OPEN_FIRST_LINK:
        				url = getFirstURLFromTweet(t);
        				break;
        			case ACTION_MODE_OPEN_TWEETDECK_PROFILE:
        				url = "tweetdeck://at/"+t.getUser().getScreenName();
        				break;
        			case ACTION_MODE_OPEN_TWIDROIDPRO_PROFILE:
        				url = "twitter://com.twidroidpro.twidroidprofile/"+t.getUser().getScreenName();
        				break;
        			case ACTION_MODE_OPEN_HOOTSUITE_PROFILE:
        				url = "x-hoot-full://profile-twitter/"+t.getUser().getScreenName();
        				break;
        			default:
        				url = LINK_URL_BASE+t.getUser().getScreenName()+"/status/"+t.getId();
        			}
        			
        			sendAnnounce(t.user.getScreenName(), t.getText(), t.getCreatedAt().getTime(), url);
        			sent_count += 1;
        			
        			// capture the largest status id sent to liveview
        			if (t_id.compareTo(new_last_id) == 1) {
        				new_last_id = new BigInteger(t_id.toString());
        			}
        			
        			Log.d(PluginConstants.LOG_TAG, "Sending tweet to LiveView : " +t.getId()+ " | "+ epoch2date(t.getCreatedAt().getTime()) + " | "+ t.user.getScreenName() + " : " + t.getText());
        		}
        	}
        	return new_last_id;
    	}
    	
        @Override
        public void run() {
        	
        	// flag that gets thrown if one of the network operations
        	// fails - caused new update to be scheduled in RECHECK_DELAY ms
        	boolean an_update_failed = false;
        	boolean logged_in = false; // until determined true
        	long RECHECK_DELAY = 60*1000; // 1 min
        	
        	// if network is down, try again in a minute
        	if (!isOnline()) {
        		scheduleTimer(60000);
        		return;
        	}
        	
        	readSharedPreferences();

            OAuthSignpostClient oauthClient = new OAuthSignpostClient(PluginConstants.JTWITTER_OAUTH_KEY, 
            		PluginConstants.JTWITTER_OAUTH_SECRET, TWITTER_OAUTH_ACCESS_TOKEN, TWITTER_OAUTH_ACCESS_TOKEN_SECRET);
            
            Twitter twitter;
            if (mUsername != null && TWITTER_OAUTH_ACCESS_TOKEN_SECRET != "") {
            	twitter = new Twitter(mUsername, oauthClient);
            } else {
            	Toast.makeText(getApplicationContext(), 
            	               "Failed to authenticate", 
            	                     Toast.LENGTH_LONG).show();
            	return;
            }
            
        	twitter.setAPIRootUrl(API_URL);
        	twitter.setCount(mMaxTweets);
        	ArrayList<Status> tweets = new ArrayList<Status>();
        	ArrayList<Status> mentions = new ArrayList<Status>();
        	ArrayList<Message> messages = new ArrayList<Message>();
        	
        	Log.d(PluginConstants.LOG_TAG, "BEGIN GET TWEETS : " + epoch2date(System.currentTimeMillis()));
        	
        	// The isValidLogin() method actually tests by retrieving direct messages,
        	// so we really only want to call it once
        	try {
        		logged_in = twitter.isValidLogin();
        		if (logged_in) {
        			Log.d(PluginConstants.LOG_TAG, "Authenticated as: " + mUsername);
        		}
        	} catch(Exception re) {
                Log.e(PluginConstants.LOG_TAG, "Failed to check authentication.", re);
                an_update_failed = true;
            }
        	
            try {
            	if (logged_in && mSharedPreferences.getBoolean("notify_directs", false)) {
            		messages.addAll(twitter.getDirectMessages());
            		Log.d(PluginConstants.LOG_TAG, "Direct messages downloaded. Time of oldest message : "+ epoch2date(messages.get(messages.size()-1).getCreatedAt().getTime())+" : " + messages.get(messages.size()-1).getId());
            	}
            } catch(Exception re) {
                Log.e(PluginConstants.LOG_TAG, "Failed to get direct messages.", re);
                an_update_failed = true;
            }
            
        	try {
            	if (logged_in && mSharedPreferences.getBoolean("notify_mentions", false) && !mSharedPreferences.getBoolean("notify_tweets", false)) {
            		mentions.addAll(twitter.getMentions());
            		Log.d(PluginConstants.LOG_TAG, "Mentions downloaded. Time of oldest mention : "+ epoch2date(mentions.get(mentions.size()-1).getCreatedAt().getTime())+" : " + mentions.get(mentions.size()-1).getId());
            	}
            } catch(Exception re) {
                Log.e(PluginConstants.LOG_TAG, "Failed to get mentions.", re);
                an_update_failed = true;
            }
            
            try {
            	Log.d(PluginConstants.LOG_TAG, "BEGIN: Checking twitter ... ");
            	
            	if (mSharedPreferences.getBoolean("notify_list", false) && !mList.equals("")) {
                	TwitterList list = TwitterList.get(mUsername, mList, twitter);
                	tweets.addAll(list.getStatuses().subList(0, mMaxTweets));
                	//Log.d(PluginConstants.LOG_TAG, "List downloaded. Total tweets on queue now : "+ tweets.size());
                	Log.d(PluginConstants.LOG_TAG, "List downloaded. Time of oldest tweet : "+ epoch2date(tweets.get(tweets.size()-1).getCreatedAt().getTime())+" : " + tweets.get(tweets.size()-1).getId());
            	}
            	if (logged_in && mSharedPreferences.getBoolean("notify_tweets", false)) {
            		tweets.addAll(twitter.getHomeTimeline()); 
            		Log.d(PluginConstants.LOG_TAG, "Timeline for user downloaded. Time of oldest tweet : "+ epoch2date(tweets.get(tweets.size()-1).getCreatedAt().getTime())+" : " + tweets.get(tweets.size()-1).getId());
            	}
            } catch(Exception re) {
                Log.e(PluginConstants.LOG_TAG, "Failed to get list : "+mList, re);
                an_update_failed = true;
            }
            
            Log.d(PluginConstants.LOG_TAG, "END GET TWEETS: Total tweets/mentions/messages retrieved: "+ (tweets.size()+mentions.size()+messages.size()) );
            
            try {
            	Log.d(PluginConstants.LOG_TAG, "BEGIN SEND TO LIVEVIEW");

            	//long last_status = mSharedPreferences.getLong("last_status", 0);
            	BigInteger last_id = new BigInteger(mSharedPreferences.getString("last_id", "0"));
            	BigInteger last_mention_id = new BigInteger(mSharedPreferences.getString("last_mention_id", "0"));
            	BigInteger last_direct_id = new BigInteger(mSharedPreferences.getString("last_direct_id", "0"));
            	BigInteger new_last_id = new BigInteger("0");
            	BigInteger new_last_mention_id = new BigInteger("0");
            	BigInteger new_last_direct_id = new BigInteger("0");
            	Log.d(PluginConstants.LOG_TAG, "last_id : " + last_id.toString());
            	Log.d(PluginConstants.LOG_TAG, "last_mention_id : " + last_mention_id.toString());
            	Log.d(PluginConstants.LOG_TAG, "last_direct_id : " + last_direct_id.toString());
            	
            	new_last_id = notifyTweets(tweets, last_id);
            	new_last_mention_id = notifyTweets(mentions, last_mention_id);
            	
            	BigInteger t_id;
            	for (Message m : messages) {
            		t_id = new BigInteger(m.getId()+"");
            		if ( t_id.compareTo(last_direct_id) == 1 ) {
            			String url = LINK_URL_BASE+"#!/messages";
            			sendAnnounce(m.getSender().getScreenName() + " (direct)", m.getText(), m.getCreatedAt().getTime(), url);
            			Log.d(PluginConstants.LOG_TAG, "Sending direct message to LiveView : " +m.getId()+ " | "+ epoch2date(m.getCreatedAt().getTime()) + " | " + m.getSender().getScreenName() + " : " + m.getText());
            			
            			// capture the largest status id sent to liveview
            			if (t_id.compareTo(new_last_direct_id) == 1) {
            				new_last_direct_id = new BigInteger(t_id.toString());
            			}
            		}
            	}
            	
            	Log.d(PluginConstants.LOG_TAG, "END SEND TO LIVEVIEW");
            	            	
            	// record of most recently notified tweet
        		if (new_last_id.compareTo(last_id) == 1) {
        			SharedPreferences.Editor prefs = mSharedPreferences.edit();
        			prefs.putString("last_id", new_last_id.toString());
        			prefs.commit();
        			Log.d(PluginConstants.LOG_TAG, "New last_id : " + new_last_id.toString() );
        		}
            	// record of most recently notified mention
        		if (new_last_mention_id.compareTo(last_mention_id) == 1) {
        			SharedPreferences.Editor prefs = mSharedPreferences.edit();
        			prefs.putString("last_mention_id", new_last_mention_id.toString());
        			prefs.commit();
        			Log.d(PluginConstants.LOG_TAG, "New last_mention_id : " + new_last_mention_id.toString() );
        		}
        		// record of most recently notified direct message
        		if (new_last_direct_id.compareTo(last_direct_id) == 1) {
        			SharedPreferences.Editor prefs = mSharedPreferences.edit();
        			prefs.putString("last_direct_id", new_last_direct_id.toString());
        			prefs.commit();
        			Log.d(PluginConstants.LOG_TAG, "New last_direct_id : " + new_last_direct_id.toString() );
        		}
        		
            } catch(Exception re) {
                Log.e(PluginConstants.LOG_TAG, "Failed to send tweet to LiveView.", re);
            }
            
            // if one of the network operations failed, we schedule another
            // check in RECHECK_DELAY milliseconds time (usually 1 min)
            // else schedule next update with default delay
            if (an_update_failed) {
            	scheduleTimer(RECHECK_DELAY);
            } else {
            	scheduleTimer();
            }

        }
        
    };
}