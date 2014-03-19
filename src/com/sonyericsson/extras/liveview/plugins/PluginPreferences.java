/*
 * Copyright (c) 2010 Sony Ericsson
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

package com.sonyericsson.extras.liveview.plugins;

import oauth.signpost.OAuth;

import winterwell.jtwitter.OAuthSignpostClient;

import com.sonyericsson.extras.liveview.plugins.wristtweets.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

/**
 * Implements PreferenceActivity and sets the project preferences to the 
 * shared preferences of the current user session.
 */
public class PluginPreferences extends PreferenceActivity {
    static final int DIALOG_ABOUT_ID = 0;
    private OAuthSignpostClient oauthClient = null;
    
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResources().getIdentifier("preferences", "xml", getPackageName()));
        
        oauthClient = new OAuthSignpostClient(PluginConstants.JTWITTER_OAUTH_KEY, 
        		PluginConstants.JTWITTER_OAUTH_SECRET, PluginConstants.OAUTH_CALLBACK_URI);
        
        Preference about_button = (Preference) findPreference("about");
        about_button.setOnPreferenceClickListener(new OnPreferenceClickListener() {

                                public boolean onPreferenceClick(Preference preference) {
                                	showDialog(DIALOG_ABOUT_ID);
                                    return true;
                                }

                        });
        
        Preference auth_button = (Preference) findPreference("authenticate");
        auth_button.setOnPreferenceClickListener(new OnPreferenceClickListener() {

                                public boolean onPreferenceClick(Preference preference) {
                                	doOAuth();
                                    return true;
                                }

                        });
        
        EditTextPreference username = (EditTextPreference) findPreference("username");
        username.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
        	
        	@Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
            	doOAuth();
                return true;
            }

    });
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
	    Dialog dialog;
	    switch(id) {
	    case DIALOG_ABOUT_ID:
	        // build the about dialog
            LayoutInflater factory = LayoutInflater.from(this);
            final View layout = factory.inflate(R.layout.about_dialog, null);
            
            TextView text = (TextView) layout.findViewById(R.id.text);
            text.setText(R.string.about_text);
            Linkify.addLinks(text, Linkify.WEB_URLS);

            dialog = new AlertDialog.Builder(PluginPreferences.this)
                .setIcon(R.drawable.icon)
                .setTitle("About Wrist Tweets")
                .setView(layout)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        // dialog automatically dismisses itself
                    }
                })
                .create(); 
	    	
	        break;
	    default:
	        dialog = null;
	    }
	    return dialog;
	}
	
	protected void doOAuth() {

        // Open the authorisation page in the user's browser
		// After successful login, Twitter will forward to the callback URL
		// which has a custom scheme (wristtweets://). This will be caught 
		// the app using an intent-filter and onNewIntent, and the oauth_verifier key will 
		// be captured from the URL query parameters.
        Uri uri = Uri.parse(oauthClient.authorizeUrl().toString());
		final Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
		browserIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_FROM_BACKGROUND);
		Log.d(PluginConstants.LOG_TAG, "Launching browser to do OAuth, callback is: " + PluginConstants.OAUTH_CALLBACK_URI);
		startActivity(browserIntent);

	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		Uri uri = null;
		if (Intent.ACTION_VIEW.equals(intent.getAction())) {
			try {
				uri = intent.getData();
				Log.d(PluginConstants.LOG_TAG, "Caught OAuth intent onNewIntent : " + uri.toString());
			} catch(Exception e){
				Log.d(PluginConstants.LOG_TAG, "OAuth callback error: "+e.getMessage());
			}
		}
		
		//Check if you got NewIntent event due to Twitter Call back only
		if (uri != null && uri.toString().startsWith(PluginConstants.OAUTH_CALLBACK_URI)) {
			String[] accessToken = {"",""};
			try {
				String verifier = uri.getQueryParameter(OAuth.OAUTH_VERIFIER);
				//Log.d(PluginConstants.LOG_TAG, "OAuth verifier : " + verifier);
				oauthClient.setAuthorizationCode(verifier);
				accessToken = oauthClient.getAccessToken();
				SharedPreferences.Editor prefs = PreferenceManager.getDefaultSharedPreferences(this).edit();
				prefs.putString("TWITTER_OAUTH_ACCESS_TOKEN", accessToken[0]);
				prefs.putString("TWITTER_OAUTH_ACCESS_TOKEN_SECRET", accessToken[1]);
				prefs.commit();
				Log.d(PluginConstants.LOG_TAG, "Successfully authenticated using OAuth: " + accessToken[0]);
			} catch(Exception e){
				Log.d(PluginConstants.LOG_TAG, "OAuth callback error: "+e.getMessage());
			}
		}
	}

}
