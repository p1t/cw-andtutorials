package apt.tutorial.two;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import winterwell.jtwitter.Twitter;
import apt.tutorial.IPostListener;
import apt.tutorial.IPostMonitor;

public class Patchy extends Activity {
	public static final String LATITUDE="apt.tutorial.latitude";
	public static final String LONGITUDE="apt.tutorial.longitude";
	public static final String STATUS_TEXT="apt.tutorial.statusText";
	private EditText status=null;
	private SharedPreferences prefs=null;
	private Twitter client=null;
	private List<TimelineEntry> timeline=new ArrayList<TimelineEntry>();
	private TimelineAdapter adapter=null;
	private IPostMonitor service=null;
	private LocationManager locMgr=null;
	private Pattern regexLocation=Pattern.compile("L\\:((\\-)?[0-9]+(\\.[0-9]+)?)\\,((\\-)?[0-9]+(\\.[0-9]+)?)");
	private View statusRow=null;
	private Animation fadeOut=null;
	private Animation fadeIn=null;
	private ServiceConnection svcConn=new ServiceConnection() {
		public void onServiceConnected(ComponentName className,
																		IBinder binder) {
			service=(IPostMonitor)binder;
			
			try {
				service.registerAccount(prefs.getString("user", null),
																prefs.getString("password", null),
																listener);
			}
			catch (Throwable t) {
				Log.e("Patchy", "Exception in call to registerAccount()", t);
				goBlooey(t);
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			service=null;
		}
	};
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		status=(EditText)findViewById(R.id.status);
		
		Button send=(Button)findViewById(R.id.send);
		
		send.setOnClickListener(onSend);
		
		prefs=PreferenceManager.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(prefListener);
		
		registerReceiver(receiver, new IntentFilter(PostMonitor.STATUS_UPDATE));
		bindService(new Intent(this, PostMonitor.class), svcConn,
								BIND_AUTO_CREATE);
	
		adapter=new TimelineAdapter();
		
		ListView list=(ListView)findViewById(R.id.timeline);

		list.setAdapter(adapter);
		list.setOnItemClickListener(onStatusClick);
		
		clearNotification();
		
		locMgr=(LocationManager)getSystemService(LOCATION_SERVICE);
		locMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER,
																	10000, 10000.0f,
																	onLocationChange);
		
		statusRow=findViewById(R.id.status_row);
		fadeOut=AnimationUtils.loadAnimation(this, R.anim.fade_out);
		fadeOut.setAnimationListener(fadeOutListener);
		fadeIn=AnimationUtils.loadAnimation(this, R.anim.fade_in);
	}
	
	@Override
	public void onNewIntent(Intent i) {
		clearNotification();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		locMgr.removeUpdates(onLocationChange);
		service.removeAccount(listener);
		unbindService(svcConn);
		unregisterReceiver(receiver);
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem statusItem=menu.findItem(R.id.status_entry);
		
		if (statusRow.getVisibility()==View.VISIBLE) {
			statusItem.setIcon(R.drawable.status_hide);
			statusItem.setTitle("Hide Status Entry");
		}
		else {
			statusItem.setIcon(R.drawable.status_show);
			statusItem.setTitle("Show Status Entry");
		}
		
		return(super.onPrepareOptionsMenu(menu));
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		new MenuInflater(getApplication())
																	.inflate(R.menu.option, menu);

		return(super.onCreateOptionsMenu(menu));
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId()==R.id.prefs) {
			startActivity(new Intent(this, EditPreferences.class));
			
			return(true);
		}
		else if (item.getItemId()==R.id.location) {
			insertLocation();
			
			return(true);
		}
		else if (item.getItemId()==R.id.help) {
			startActivity(new Intent(this, HelpPage.class));

			return(true);
		}
		else if (item.getItemId()==R.id.status_entry) {
			toggleStatusEntry();

			return(true);
		}
		
		return(super.onOptionsItemSelected(item));
	}
	
	private void insertLocation() {
		Location loc=locMgr.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		
		if (loc==null) {
			Toast
				.makeText(this, "No location available", Toast.LENGTH_SHORT)
				.show();
		}
		else {
			StringBuilder buf=new StringBuilder(status
																				 .getText()
																				 .toString());
			buf.append(" L:");
			buf.append(String.valueOf(loc.getLatitude()));
			buf.append(",");
			buf.append(String.valueOf(loc.getLongitude()));
			status.setText(buf.toString());
		}
	}
	
	private void clearNotification() {
		NotificationManager mgr=
			(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
			
		mgr.cancel(PostMonitor.NOTIFICATION_ID);
	}
	
	synchronized private Twitter getClient() {
		if (client==null) {
			client=new Twitter(prefs.getString("user", ""),
													prefs.getString("password", ""));
			client.setAPIRootUrl("https://identi.ca/api");
		}
		
		return(client);
	}
	
	synchronized private void resetClient() {
		client=null;
		service.removeAccount(listener);
		service.registerAccount(prefs.getString("user", ""),
														prefs.getString("password", ""),
														listener);
	}
	
	private void toggleStatusEntry() {
		if (statusRow.getVisibility()==View.VISIBLE) {
			statusRow.startAnimation(fadeOut);
		}
		else {
			statusRow.setVisibility(View.VISIBLE);
			statusRow.startAnimation(fadeIn);
		}
	}
	
	private void updateStatus() {
		try {
			getClient().updateStatus(status.getText().toString());
		}
		catch (Throwable t) {
			Log.e("Patchy", "Exception in updateStatus()", t);
			goBlooey(t);
		}
	}
	
	private void goBlooey(Throwable t) {
		AlertDialog.Builder builder=new AlertDialog.Builder(this);
		
		builder
			.setTitle("Exception!")
			.setMessage(t.toString())
			.setPositiveButton("OK", null)
			.show();
	}
	
	private View.OnClickListener onSend=new View.OnClickListener() {
		public void onClick(View v) {
			updateStatus();
		}
	};
	
	private SharedPreferences.OnSharedPreferenceChangeListener prefListener=
		new SharedPreferences.OnSharedPreferenceChangeListener() {
		public void onSharedPreferenceChanged(SharedPreferences sharedPrefs,
																						String key) {
			if (key.equals("user") || key.equals("password")) {
				resetClient();
			}
		}
	};
	
	private IPostListener listener=new IPostListener() {
		public void newFriendStatus(final String friend, final String status,
																final String createdAt) {
			runOnUiThread(new Runnable() {
				public void run() {
					adapter.insert(new TimelineEntry(friend,
																					 createdAt,
																					 status),
													0);
				}
			});
		}
	};
	
	Animation.AnimationListener fadeOutListener=new Animation.AnimationListener() {
		public void onAnimationEnd(Animation animation) {
			statusRow.setVisibility(View.GONE);
		}
		public void onAnimationRepeat(Animation animation) {
			// not needed
		}
		public void onAnimationStart(Animation animation) {
			// not needed
		}
	};
	
	private LocationListener onLocationChange=new LocationListener() {
		public void onLocationChanged(Location location) {
			// required for interface, not used
		}
		public void onProviderDisabled(String provider) {
			// required for interface, not used
		}
		public void onProviderEnabled(String provider) {
			// required for interface, not used
		}
		public void onStatusChanged(String provider, int status,
																 Bundle extras) {
			// required for interface, not used
		}
	};
	
	private AdapterView.OnItemClickListener onStatusClick=
										new AdapterView.OnItemClickListener() {
		public void onItemClick(AdapterView<?> parent, View view,
														int position, long id) {
			TimelineEntry entry=timeline.get(position);
			Matcher r=regexLocation.matcher(entry.status);
			
			if (r.find()) {
				double latitude=Double.valueOf(r.group(1));
				double longitude=Double.valueOf(r.group(4));
				
				Intent i=new Intent(Patchy.this, StatusMap.class);
				
				i.putExtra(LATITUDE, latitude);
				i.putExtra(LONGITUDE, longitude);
				i.putExtra(STATUS_TEXT, entry.status);
				
				startActivity(i);
			}
		}
	};
	
	private BroadcastReceiver receiver=new BroadcastReceiver() {
		public void onReceive(Context context,
													final Intent intent) {
			String friend=intent.getStringExtra(PostMonitor.FRIEND);
			String createdAt=intent.getStringExtra(PostMonitor.CREATED_AT);
			String status=intent.getStringExtra(PostMonitor.STATUS);
			
			adapter.insert(new TimelineEntry(friend, createdAt, status),
											0);
		}
	};
	
	class TimelineEntry {
		String friend="";
		String createdAt="";
		String status="";
		
		TimelineEntry(String friend, String createdAt,
									String status) {
			this.friend=friend;
			this.createdAt=createdAt;
			this.status=status;
		}
	}
	
	class TimelineAdapter extends ArrayAdapter<TimelineEntry> {
		 TimelineAdapter() {
			super(Patchy.this, R.layout.row, timeline);
		}
		
		public View getView(int position, View convertView,
												ViewGroup parent) {
			View row=convertView;
			TimelineEntryWrapper wrapper=null;
			
			if (row==null) {													
				LayoutInflater inflater=getLayoutInflater();
				
				row=inflater.inflate(R.layout.row, parent, false);
				wrapper=new TimelineEntryWrapper(row);
				row.setTag(wrapper);
			}
			else {
				wrapper=(TimelineEntryWrapper)row.getTag();
			}
			
			wrapper.populateFrom(timeline.get(position));
			
			return(row);
		}
	}
	
	class TimelineEntryWrapper {
		private TextView friend=null;
		private TextView createdAt=null;
		private TextView status=null;
		private View row=null;
		
		TimelineEntryWrapper(View row) {
			this.row=row;
		}
		
		void populateFrom(TimelineEntry s) {
			getFriend().setText(s.friend);
			getCreatedAt().setText(s.createdAt);
			getStatus().setText(s.status);
		}
		
		TextView getFriend() {
			if (friend==null) {
				friend=(TextView)row.findViewById(R.id.friend);
			}
			
			return(friend);
		}
		
		TextView getCreatedAt() {
			if (createdAt==null) {
				createdAt=(TextView)row.findViewById(R.id.created_at);
			}
			
			return(createdAt);
		}
		
		TextView getStatus() {
			if (status==null) {
				status=(TextView)row.findViewById(R.id.status);
			}
			
			return(status);
		}
	}
}