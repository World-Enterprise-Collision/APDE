package com.calsignlabs.apde;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Map.Entry;

import com.calsignlabs.apde.build.Manifest;
import com.ipaulpro.afilechooser.utils.FileUtils;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class SketchPropertiesActivity extends PreferenceActivity {
	//This is a number, that's all that matters
	private static final int REQUEST_CHOOSER = 6283;
	
	private static final boolean ALWAYS_SIMPLE_PREFS = true;
	
	private ActionBarDrawerToggle drawerToggle;
	@SuppressWarnings("unused")
	private boolean drawerOpen;
	
	private OnSharedPreferenceChangeListener prefListener;
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_sketch_properties);
		
		if(android.os.Build.VERSION.SDK_INT >= 11) { //Yet another unfortunate casualty of AppCompat
			getActionBar().setTitle(getGlobalState().getSketchName());
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}
		
		getGlobalState().setProperties(this);
		
		final DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_sketch_properties);
        final ListView drawerList = (ListView) findViewById(R.id.drawer_list);
        
        forceDrawerReload();
        
        drawerList.setOnScrollListener(new OnScrollListener() {
			@Override
			public void onScroll(AbsListView arg0, int arg1, int arg2, int arg3) {}
			
			@Override
			public void onScrollStateChanged(AbsListView listView, int scrollState) {
				if(scrollState == SCROLL_STATE_IDLE) {
					//Select the current sketch TODO this isn't working yet
                    if(getGlobalState().getSelectedSketch() < drawerList.getCount() && getGlobalState().getSelectedSketch() >= 0) {
                    	View view = listView.getChildAt(getGlobalState().getSelectedSketch());
                    	if(view != null)
                    		view.setSelected(true);
                    }
				}
        }});
        
		drawerToggle = new ActionBarDrawerToggle(this, drawer, R.drawable.ic_navigation_drawer, R.string.nav_drawer_open, R.string.nav_drawer_close) {
            @Override
        	public void onDrawerClosed(View view) {
            	if(android.os.Build.VERSION.SDK_INT >= 11) //Yet another unfortunate casualty of AppCompat
            		invalidateOptionsMenu();
            }
            
            @Override
            public void onDrawerSlide(View drawer, float slide) {
            	super.onDrawerSlide(drawer, slide);
            	
            	//Detect an initial open event
            	if(slide > 0) {
            		if(android.os.Build.VERSION.SDK_INT >= 11) //Yet another unfortunate casualty of AppCompat
            			invalidateOptionsMenu();
                    drawerOpen = true;
                    
                    //Select the current sketch
                    if(getGlobalState().getSelectedSketch() < drawerList.getCount() && getGlobalState().getSelectedSketch() >= 0) {
                    	ListView drawerList = (ListView) findViewById(R.id.drawer_list);
                    	View view = drawerList.getChildAt(getGlobalState().getSelectedSketch());
                    	if(view != null)
                    		view.setSelected(true);
                    }
            	} else {
            		if(android.os.Build.VERSION.SDK_INT >= 11) //Yet another unfortunate casualty of AppCompat
            			invalidateOptionsMenu();
                    drawerOpen = false;
            	}
            }
            
            @Override
            public void onDrawerOpened(View drawerView) {
            	if(android.os.Build.VERSION.SDK_INT >= 11) //Yet another unfortunate casualty of AppCompat
            		invalidateOptionsMenu();
        }};
        drawer.setDrawerListener(drawerToggle);
        
        drawerList.setOnItemClickListener(new ListView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				getGlobalState().getEditor().autoSave();
				
				String sketchName = ((TextView) view).getText().toString();
				
				//If it is further down on the list, it must be an example
				if(position > getGlobalState().getEditor().getSketchCount() + 1)
					getGlobalState().getEditor().loadExample(sketchName);
				else
					getGlobalState().getEditor().loadSketch(sketchName);
				
				view.setSelected(true);
				getGlobalState().setSelectedSketch(position);
				
				if(android.os.Build.VERSION.SDK_INT >= 11) { //Yet another unfortunate casualty of AppCompat
					getActionBar().setTitle(getGlobalState().getSketchName());
					invalidateOptionsMenu();
				}
				
				drawer.closeDrawers();
				
				forceDrawerReload();
				restartActivity();
		}});
        
        getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.activity_background));
	}
	
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		
		setupSimplePreferencesScreen();
	}
	
	@Override
    public void onStop() {
		getGlobalState().getEditor().saveSketchForStop();
    	
    	super.onStop();
    }
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode) {
		case REQUEST_CHOOSER:
			if(resultCode == RESULT_OK) {
				final Uri uri = data.getData();
				
				// Get the File path from the Uri
				String path = FileUtils.getPath(this, uri);
				
				if(path != null && FileUtils.isLocal(path)) {
					File file = new File(path);
					if(file.exists())
						addFile(file);
				}
			}
			break;
		}
	}
	
	public void addFile(File source) {
		//Get the location of this sketch's data folder
		File dataFolder = new File(getGlobalState().getSketchbookFolder().getAbsolutePath() + "/" + getGlobalState().getSketchName() + "/data/");
		dataFolder.mkdir();
		
		File dest = new File(dataFolder, source.getName());
		
		try {
			copyFile(source, dest);
		} catch (IOException e) {
			//Something bad happened
			System.err.println("Failed to add file to sketch, error output:");
			e.printStackTrace();
		}
	}
	
	public static void copyFile(File sourceFile, File destFile) throws IOException {
		if(!destFile.exists())
			destFile.createNewFile();
		
		FileChannel source = null;
		FileChannel destination = null;
		
		try {
			source = new FileInputStream(sourceFile).getChannel();
			destination = new FileOutputStream(destFile).getChannel();
			destination.transferFrom(source, 0, source.size());
		} finally {
			if(source != null)
				source.close();
			if(destination != null)
				destination.close();
		}
	}
	
	public static void updatePrefs(APDE global) {
		//Don't try if the name is "sketch"... it will crash and burn
		//Why? "sketch" isn't a valid sketch name - it's the name of the temporary sketch that isn't saved in the sketchbook folder
		if(global.getSketchName().equals("sketch"))
			return;
		
		Manifest mf = global.getManifest();
		
		SharedPreferences.Editor edit = global.getSharedPreferences(global.getSketchName(), 0).edit();
		edit.putString("prop_pretty_name", mf.getPrettyName());
		edit.putString("permissions", mf.getCustomPermissions());
		edit.putString("prop_target_sdk", Integer.toString(mf.getTargetSdk(global)));
		edit.putString("prop_orientation", mf.getOrientation(global));
		edit.commit();
	}
	
	@SuppressWarnings("deprecation")
	private void setupSimplePreferencesScreen() {
		if(!isSimplePreferences(this))
			return;
		
		//Switch to the preferences for the current sketch
		getPreferenceManager().setSharedPreferencesName(getGlobalState().getSketchName());
		
		updatePrefs(getGlobalState());
		
		// In the simplified UI, fragments are not used at all and we instead
		// use the older PreferenceActivity APIs.
		
		// Add preferences.
		addPreferencesFromResource(R.xml.sketch_properties);
		
		// Bind the summaries of EditText/List/Dialog/Ringtone preferences to
		// their values. When their values change, their summaries are updated
		// to reflect the new value, per the Android Design guidelines.
		bindPreferenceSummaryToValue(findPreference("prop_pretty_name"));
//		bindPreferenceSummaryToValue(findPreference("prop_min_sdk"));
		bindPreferenceSummaryToValue(findPreference("prop_target_sdk"));
		bindPreferenceSummaryToValue(findPreference("prop_orientation"));
		
		//Hacky way of setting up the summaries initially
		String prettyName = ((EditTextPreference) findPreference("prop_pretty_name")).getText(); //We check this to initialize the default value with the name of the sketch
		findPreference("prop_pretty_name").setSummary(prettyName.equals(".") ? getGlobalState().getSketchName() : prettyName); //The "." default is because we can't reference this value from XML
//		findPreference("prop_min_sdk").setSummary(((EditTextPreference) findPreference("prop_min_sdk")).getText());
		findPreference("prop_target_sdk").setSummary(((EditTextPreference) findPreference("prop_target_sdk")).getText());
		findPreference("prop_orientation").setSummary(((ListPreference) findPreference("prop_orientation")).getEntry());
		
		//Get rid of the default "." (hopefully no one decides to name their sketch "."...)
		if(prettyName.equals("."))
			((EditTextPreference) findPreference("prop_pretty_name")).setText(getGlobalState().getSketchName());
		
		Preference launchPermissions = (Preference) findPreference("prop_permissions");
		launchPermissions.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) { 
				launchPermissions();
				return true;
			}
		});
		
		Preference launchAddFile = (Preference) findPreference("prop_add_file");
		launchAddFile.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) { 
				launchAddFile();
				return true;
			}
		});
		
		Preference launchSketchFolder = (Preference) findPreference("prop_show_sketch_folder");
		launchSketchFolder.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) { 
				launchSketchFolder();
				return true;
			}
		});
		
		//If this is an example... or if this is a temporary sketch...
		if(getGlobalState().isExample() || getGlobalState().getSketchName().equals("sketch")) {
        	//...disable all of the preferences
        	findPreference("prop_manifest").setEnabled(false);
        	findPreference("prop_sketch_folder").setEnabled(false);
        }
		
		//This can't be an anonymous class because SharedPreferences keeps listeners in a WeakHashMap...
		//...or a local instance, for that matter
		//StackOverflow: http://stackoverflow.com/questions/2542938/sharedpreferences-onsharedpreferencechangelistener-not-being-called-consistently
		prefListener = new OnSharedPreferenceChangeListener() {
			@Override
			public void onSharedPreferenceChanged(SharedPreferences pref, String key) {
				//If this is the temporary sketch, bail out
				if(getGlobalState().getSketchName().equals("sketch"))
					return;
				
				Manifest mf = getGlobalState().getManifest();
				
				if(key.equals("prop_pretty_name"))
					mf.setPrettyName(pref.getString(key, "."));
				if(key.equals("permissions"))
					mf.setCustomPermissions(pref.getString(key, "").split(","));
				if(key.equals("prop_target_sdk"))
					mf.setTargetSdk(Integer.parseInt(pref.getString("prop_target_sdk", getResources().getString(R.string.prop_target_sdk_default))));
				if(key.equals("prop_orientation"))
					mf.setOrientation(pref.getString("prop_orientation", getResources().getString(R.string.prop_orientation_default)));
				
				mf.save();
			}
		};
		
		//Detect changes to the preferences so that we can save them to the manifest file directly
		//TODO This isn't an optimal solution - we still use SharedPreferences
		getSharedPreferences(getGlobalState().getSketchName(), 0).registerOnSharedPreferenceChangeListener(prefListener);
	}
	
	private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
		@Override
		public boolean onPreferenceChange(Preference preference, Object value) {
			String stringValue = value.toString();
			
			if (preference instanceof ListPreference) {
				// For list preferences, look up the correct display value in
				// the preference's 'entries' list.
				ListPreference listPreference = (ListPreference) preference;
				int index = listPreference.findIndexOfValue(stringValue);
				
				// Set the summary to reflect the new value.
				preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);
			} else {
				// For all other preferences, set the summary to the value's
				// simple string representation.
				preference.setSummary(stringValue);
			}
			return true;
		}
	};
	
	private static void bindPreferenceSummaryToValue(Preference preference) {
		// Set the listener to watch for value changes.
		preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
		
		// Trigger the listener immediately with the preference's
		// current value.
		sBindPreferenceSummaryToValueListener.onPreferenceChange(
				preference,
				PreferenceManager.getDefaultSharedPreferences(
						preference.getContext()).getString(preference.getKey(), ""));
	}
	
	private static boolean isXLargeTablet(Context context) {
		return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
	}
	
	@SuppressWarnings("unused")
	private static boolean isSimplePreferences(Context context) {
		return ALWAYS_SIMPLE_PREFS
				|| Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
				|| !isXLargeTablet(context);
	}
	
	public APDE getGlobalState() {
		return (APDE) getApplication();
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_sketch_properties, menu);
        
        if(getGlobalState().isExample()) {
        	//Don't let them mess with the examples!
        	
        	menu.findItem(R.id.menu_change_sketch_name).setVisible(false);
        	menu.findItem(R.id.menu_delete).setVisible(false);
        } else {
        	menu.findItem(R.id.menu_change_sketch_name).setVisible(true);
        	menu.findItem(R.id.menu_delete).setVisible(true);
        }
        
        return true;
    }
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
            	finish();
                return true;
            case R.id.menu_change_sketch_name:
            	changeSketchName();
            	return true;
            case R.id.action_settings:
            	launchSettings();
            	return true;
            case R.id.menu_save:
            	saveSketch();
            	return true;
            case R.id.menu_load:
            	loadSketch();
        		return true;
        	case R.id.menu_export:
        		exportSketch();
        		return true;
        	case R.id.menu_delete:
        		deleteSketch();
        		return true;
        	case R.id.menu_new:
        		newSketch();
        		return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
	
	private void launchSettings() {
		if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB)
			startActivity(new Intent(this, SettingsActivity.class));
		else
			startActivity(new Intent(this, SettingsActivityHC.class));
	}
	
	private void launchPermissions() {
		Intent intent = new Intent(this, PermissionsActivity.class);
		startActivity(intent);
	}
	
	public void launchAddFile() {
		//Launch file selection intent (includes AFileChooser's custom file chooser implementation)
		
		Intent intent = Intent.createChooser(FileUtils.createGetContentIntent(), getResources().getString(R.string.select_file));
	    startActivityForResult(intent, REQUEST_CHOOSER);
	}
	
	public void launchSketchFolder() {
		//TODO make this browse, not request a file...
		//TODO also, get rid of Google Drive and such - only allow local file browsers (that support the external storage)
		
		File sketchFolder = new File(getGlobalState().getSketchbookFolder(), getGlobalState().getSketchName());
		
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setDataAndType(Uri.fromFile(sketchFolder), "*/*");
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); //Start this in a separate task
		startActivity(Intent.createChooser(intent, getResources().getString(R.string.show_sketch_folder)));
	}
	
	private void saveSketch() {
		if(!externalStorageWritable()) {
    		AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getText(R.string.external_storage_dialog_title))
            	.setMessage(getResources().getText(R.string.external_storage_dialog_message)).setCancelable(false)
            	.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            		@Override
            		public void onClick(DialogInterface dialog, int which) {}
            }).show();
            
    		return;
    	}
		
		if(getGlobalState().getSketchName().equals("sketch")) {
    		AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getText(R.string.sketch_name_dialog_title))
            	.setMessage(getResources().getText(R.string.sketch_name_dialog_message)).setCancelable(false)
            	.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            	
            	@Override
                public void onClick(DialogInterface dialog, int which) {}
            }).show();
            
            return;
    	}
		
		getGlobalState().getEditor().saveSketch();
		forceDrawerReload();
	}
	
	private boolean externalStorageWritable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) return true;
		else return false;
	}
	
	private void loadSketch() {
		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_sketch_properties);
		LinearLayout drawerLayout = (LinearLayout) findViewById(R.id.drawer_wrapper_sketch_properties);
		
		drawer.openDrawer(drawerLayout);
	}
	
	@SuppressLint("NewApi")
	private void newSketch() {
		if(getGlobalState().getSketchName().equals("sketch")) {
			AlertDialog.Builder alert = new AlertDialog.Builder(this);
	    	
	    	alert.setTitle(R.string.save_sketch_dialog_title);
	    	alert.setMessage(R.string.save_sketch_dialog_message);
	    	
	    	alert.setPositiveButton(R.string.save_sketch, new DialogInterface.OnClickListener() {
	    		public void onClick(DialogInterface dialog, int whichButton) {
	    			//Save the sketch
	    			getGlobalState().getEditor().autoSave();
	    			
	    			getGlobalState().setSketchName("sketch");
	    			getGlobalState().setSelectedSketch(-1);
	    			getGlobalState().getEditor().newSketch();
	    			forceDrawerReload();
	    			
	    			if(android.os.Build.VERSION.SDK_INT >= 11) //Yet another unfortunate casualty of AppCompat
	    				getActionBar().setTitle(getGlobalState().getSketchName());
	    			
	    			finish();
	    	}});
	    	
	    	//TODO neutral and negative seem mixed up, uncertain of correct implementation - current set up is for looks
	    	alert.setNeutralButton(R.string.dont_save_sketch, new DialogInterface.OnClickListener() {
	    		public void onClick(DialogInterface dialog, int whichButton) {
	    			getGlobalState().setSketchName("sketch");
	    			getGlobalState().setSelectedSketch(-1);
	    			getGlobalState().getEditor().newSketch();
	    			forceDrawerReload();
	    			
	    			if(android.os.Build.VERSION.SDK_INT >= 11) //Yet another unfortunate casualty of AppCompat
	    				getActionBar().setTitle(getGlobalState().getSketchName());
	    			
	    			finish();
	    	}});
	    	
	    	alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
	    		public void onClick(DialogInterface dialog, int whichButton) {
	    	}});
	    	
	    	//Show the soft keyboard if the hardware keyboard is unavailable (hopefully)
	    	AlertDialog dialog = alert.create();
	    	if(!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false))
	    		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
	    	dialog.show();
		} else {
			//Save the sketch
			getGlobalState().getEditor().autoSave();
			
			getGlobalState().setSketchName("sketch");
			getGlobalState().setSelectedSketch(-1);
			getGlobalState().getEditor().newSketch();
			forceDrawerReload();
			
			if(android.os.Build.VERSION.SDK_INT >= 11) //Yet another unfortunate casualty of AppCompat
				getActionBar().setTitle(getGlobalState().getSketchName());
			
			finish();
		}
	}
	
	private void exportSketch() {
		
	}
	
	private void deleteSketch() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
    	
    	alert.setTitle(R.string.delete_sketch_dialog_title);
    	alert.setMessage(R.string.delete_sketch_dialog_message);
    	
    	alert.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
    		@SuppressLint("NewApi")
			public void onClick(DialogInterface dialog, int whichButton) {
    			getGlobalState().getEditor().deleteSketch();
    			
    			getGlobalState().setSketchName("sketch");
    			getGlobalState().setSelectedSketch(-1);
    			getGlobalState().getEditor().newSketch();
    			forceDrawerReload();
    			
    			if(android.os.Build.VERSION.SDK_INT >= 11) //Yet another unfortunate casualty of AppCompat
    				getActionBar().setTitle(getGlobalState().getSketchName());
    			
    			finish();
    	}});
    	
    	alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    	}});
    	
    	//Show the soft keyboard if the hardware keyboard is unavailable (hopefully)
    	AlertDialog dialog = alert.create();
    	if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false))
    		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    	dialog.show();
	}
	
	private void changeSketchName() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
    	
    	alert.setTitle(R.string.change_sketch_name_dialog_title);
    	alert.setMessage(R.string.change_sketch_name_dialog_message);
    	
    	final EditText input = new EditText(this);
    	input.setSingleLine();
    	input.setText(getGlobalState().getSketchName());
    	input.selectAll();
    	alert.setView(input);
    	
    	alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    			String before = getGlobalState().getSketchName();
    			String after = input.getText().toString();
    			
    			if(validateSketchName(after)) {
    				getGlobalState().setSketchName(after);
    				getGlobalState().getEditor().getSketchLoc(before).renameTo(getGlobalState().getEditor().getSketchLoc(after));
    				
    				//If the user has set the pretty name to the name of their sketch, they probably want to change the pretty name too
    				@SuppressWarnings("deprecation")
					EditTextPreference pref = ((EditTextPreference) findPreference("prop_pretty_name"));
    				if(pref.getText().equals(before))
    					pref.setText(after);
    				
    				//Don't copy preferences from a temporary sketch - there aren't any!
    				if(!before.equals("sketch"))
    					copyPrefs(before, after);
    				
    				//Make sure we save...
    				saveSketch();
    				
    				//We have to save before we do this... because it reads from the file system
    				getGlobalState().setSelectedSketch(getGlobalState().getEditor().drawerIndexOfSketch(after));
    				getGlobalState().getEditor().forceDrawerReload();
    				forceDrawerReload();
    				
    				restartActivity();
    			}
    	}});
    	
    	alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    	}});
    	
    	//Show the soft keyboard if the hardware keyboard is unavailable (hopefully)
    	AlertDialog dialog = alert.create();
    	if(!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false))
    		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    	dialog.show();
    }
	
	protected void forceDrawerReload() {
		final ListView drawerList = (ListView) findViewById(R.id.drawer_list);

        //Create an ArrayAdapter to populate the drawer's list of sketches
        SectionedListAdapter sections = new SectionedListAdapter(this);
        
        ArrayAdapter<String> sketches = new ArrayAdapter<String>(this, R.layout.drawer_list_item);
        getGlobalState().getEditor().populateWithSketches(sketches);
        
        ArrayAdapter<String> examples = new ArrayAdapter<String>(this, R.layout.drawer_list_item);
        getGlobalState().getEditor().populateWithExamples(examples);
        
        sections.addSection(getResources().getString(R.string.drawer_list_title_sketchbook), sketches);
        sections.addSection(getResources().getString(R.string.drawer_list_title_examples), examples);

        //Load the list of sketches into the drawer
        drawerList.setAdapter(sections);
        drawerList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
	}
    
	//Restart the activity with no animation
    private void restartActivity() {
        Intent intent = getIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        
        overridePendingTransition(0, 0);
        finish();
        
        overridePendingTransition(0, 0);
        startActivity(intent);
	}
	
	private boolean validateSketchName(String name) {
		if(name.length() <= 0)
			return false;
		
		if(name.equals("sketch"))
			return false;
		
		return true;
	}
	
	//Copy all of the old preferences over to the new SharedPreferences and delete the old ones
	@SuppressWarnings("deprecation")
	public void copyPrefs(String before, String after) {
		SharedPreferences old = getPreferenceManager().getSharedPreferences();
		getPreferenceManager().setSharedPreferencesName(getGlobalState().getSketchName());
		SharedPreferences.Editor ed = getPreferenceManager().getSharedPreferences().edit();

		for(Entry<String,?> entry : old.getAll().entrySet()){ 
			Object v = entry.getValue(); 
			String key = entry.getKey();

			if(v instanceof Boolean)
				ed.putBoolean(key, ((Boolean) v).booleanValue());
			else if(v instanceof Float)
				ed.putFloat(key, ((Float) v).floatValue());
			else if(v instanceof Integer)
				ed.putInt(key, ((Integer) v).intValue());
			else if(v instanceof Long)
				ed.putLong(key, ((Long) v).longValue());
			else if(v instanceof String)
				ed.putString(key, ((String) v));         
		}

		ed.commit();
		old.edit().clear().commit();
	}
}