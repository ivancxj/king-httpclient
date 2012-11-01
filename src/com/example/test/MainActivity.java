package com.example.test;

import com.king.http.KingHttpClient;
import com.king.http.HttpResponseHandler;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class MainActivity extends Activity {
	public static final String TAG = "Main";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

	}
	
	public void httpUrl(View button){
		KingHttpClient client = new KingHttpClient();
		client.get("http://192.168.1.3:8080",new HttpResponseHandler(){
			@Override
			public void onSuccess(String content) {
				Log.e("Main", content);
			}
		});
	}

}
