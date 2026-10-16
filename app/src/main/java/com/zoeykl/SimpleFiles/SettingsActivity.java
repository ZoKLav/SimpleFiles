package com.zoeykl.SimpleFiles;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;

public class SettingsActivity extends Activity {
    private Appearance theme;
    private SharedPreferences prefs;
    private LinearLayout content;
    private String browserFolder;
    private final String[] startupLabels={"Home shortcuts","Internal storage","Downloads","Default folder","Last visited folder"};
    private final String[] startupValues={"home","internal","downloads","custom","last"};
    @Override public void onCreate(Bundle state) {
        prefs=getSharedPreferences("prefs",0);
        setTheme(prefs.getBoolean("dark",false)?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);browserFolder=getIntent().getStringExtra("currentFolder");drawPage();
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private TextView label(String value,int size) {TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(theme.color("text"));return t;}
    private void drawPage() {
        theme=new Appearance(this);getWindow().getDecorView().setSystemUiVisibility(Color.luminance(theme.color("surface"))>0.5?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR:0);getWindow().setStatusBarColor(theme.color("surface"));getWindow().setNavigationBarColor(Appearance.blend(theme.color("surface"),Color.BLACK,.75f));
        LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setBackgroundColor(theme.color("background"));setContentView(page);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),0,dp(16),0);bar.setBackgroundColor(theme.color("surface"));page.addView(bar,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView back=label("‹",30);back.setGravity(Gravity.CENTER);back.setContentDescription("Back to files");bar.addView(back,new LinearLayout.LayoutParams(dp(48),-1));back.setOnClickListener(v->finish());
        TextView heading=label("Settings",21);heading.setTypeface(null,1);bar.addView(heading);
        ScrollView scroll=new ScrollView(this);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));content=new LinearLayout(this);content.setOrientation(1);content.setPadding(dp(16),dp(8),dp(16),dp(24));scroll.addView(content);
        section("Appearance");
        toggle("Dark mode",prefs.getBoolean("dark",false),v->{prefs.edit().putBoolean("dark",v).apply();recreate();});
        colorRow("Accent and selection","accent");colorRow("Folder icons","folderColor");colorRow("File icons","fileColor");colorRow("Toolbar icons","toolbarColor");
        colorRow("Page background (current mode)","background");colorRow("Panels and rows (current mode)","surface");colorRow("Text (current mode)","text");
        row("Reset appearance","Restore default colors and remove saved custom colors",()->new AlertDialog.Builder(this).setTitle("Reset appearance?").setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,w)->{
            SharedPreferences.Editor e=prefs.edit();for(String key:new String[]{"accent","folderColor","fileColor","toolbarColor","background_light","background_dark","surface_light","surface_dark","text_light","text_dark","customColors"})e.remove(key);e.apply();drawPage();}).show());
        section("Home and startup");
        String start=prefs.getString("startup","home");int at=Arrays.asList(startupValues).indexOf(start);final int index=Math.max(0,at);
        row("Open on launch",startupLabels[index],()->new AlertDialog.Builder(this).setTitle("Open on launch").setSingleChoiceItems(startupLabels,index,(d,n)->{prefs.edit().putString("startup",startupValues[n]).apply();d.dismiss();drawPage();}).setNegativeButton("Cancel",null).show());
        row("Default folder",defaultFolder(),()->editFolder());
        if(browserFolder!=null)row("Use current folder as default",browserFolder,()->{prefs.edit().putString("defaultPath",browserFolder).putString("startup","custom").apply();drawPage();});
        row("Home button opens",prefs.getBoolean("homeToDefault",false)?"Default folder":"Home shortcuts",()->new AlertDialog.Builder(this).setTitle("Home button opens").setSingleChoiceItems(new String[]{"Home shortcuts","Default folder"},prefs.getBoolean("homeToDefault",false)?1:0,(d,n)->{prefs.edit().putBoolean("homeToDefault",n==1).apply();d.dismiss();drawPage();}).setNegativeButton("Cancel",null).show());
        toggle("Show common folders on Home",prefs.getBoolean("homeCommon",true),v->prefs.edit().putBoolean("homeCommon",v).apply());
        row("Manage pinned folders",prefs.getStringSet("pinned",Collections.emptySet()).size()+" pinned",()->managePins());
        section("File browsing");
        toggle("Show hidden files",prefs.getBoolean("hidden",false),v->prefs.edit().putBoolean("hidden",v).apply());
        toggle("Show filename extensions",prefs.getBoolean("extensions",true),v->prefs.edit().putBoolean("extensions",v).apply());
        toggle("Show image thumbnails",prefs.getBoolean("thumbnails",true),v->prefs.edit().putBoolean("thumbnails",v).apply());
        row("Default view",prefs.getBoolean("grid",false)?"Large icons":"Details list",()->new AlertDialog.Builder(this).setTitle("View").setSingleChoiceItems(new String[]{"Details list","Large icons"},prefs.getBoolean("grid",false)?1:0,(d,n)->{prefs.edit().putBoolean("grid",n==1).apply();d.dismiss();drawPage();}).show());
        section("Storage");
        row("Storage access","Manage SimpleFiles access in Android settings",()->{try{Intent i=new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName()));startActivity(i);}catch(Exception e){Toast.makeText(this,"Android settings unavailable",Toast.LENGTH_SHORT).show();}});
        row("About SimpleFiles","Version 1.1.0 · Android 7.0+ · Java",()->new AlertDialog.Builder(this).setTitle("SimpleFiles 1.1.0").setMessage("Long-press an item to select it. Use the three-dot menu for file actions. Pin folders from their menu to add them to Home. Root access is available in the browsing menu on rooted devices.").setPositiveButton("Close",null).show());
    }
    private String defaultFolder(){return prefs.getString("defaultPath",Environment.getExternalStorageDirectory().getAbsolutePath());}
    private void section(String name){TextView t=label(name,14);t.setTypeface(null,1);t.setTextColor(theme.color("accent"));t.setPadding(0,dp(22),0,dp(10));content.addView(t);}
    private LinearLayout row(String name,String detail,Runnable action){
        LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(12),dp(14),dp(12),dp(14));r.setBackgroundColor(theme.color("surface"));
        LinearLayout col=new LinearLayout(this);col.setOrientation(1);r.addView(col,new LinearLayout.LayoutParams(0,-2,1));TextView title=label(name,16);col.addView(title);if(detail!=null){TextView sub=label(detail,12);sub.setTextColor(theme.muted());sub.setPadding(0,dp(4),0,0);col.addView(sub);}
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(1);content.addView(r,lp);r.setOnClickListener(v->action.run());return r;
    }
    private interface ToggleAction {void set(boolean value);}
    private void toggle(String name,boolean value,ToggleAction action){Switch sw=new Switch(this);sw.setChecked(value);sw.setContentDescription(name);LinearLayout r=row(name,null,()->sw.setChecked(!sw.isChecked()));r.addView(sw);sw.setOnCheckedChangeListener((b,v)->action.set(v));}
    private void colorRow(String title,String role){LinearLayout r=row(title,String.format(Locale.ROOT,"#%06X",theme.color(role)&0xffffff),()->pickColor(title,role));View swatch=new View(this);swatch.setBackgroundColor(theme.color(role));r.addView(swatch,new LinearLayout.LayoutParams(dp(36),dp(36)));}
    private void editFolder(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setText(defaultFolder());
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Default folder path").setMessage("Enter an absolute folder path, or use Browse to choose a folder.").setView(input).setNegativeButton("Cancel",null).setNeutralButton("Browse",(d,w)->browseFolder(new File(defaultFolder()))).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{File f=new File(input.getText().toString().trim());if(!f.isAbsolute()||!f.isDirectory()||!f.canRead()){input.setError("Choose an existing readable folder");return;}prefs.edit().putString("defaultPath",f.getAbsolutePath()).apply();dialog.dismiss();drawPage();}));dialog.show();
    }
    private void browseFolder(File folder){
        if(!folder.isDirectory()||!folder.canRead())folder=Environment.getExternalStorageDirectory();final File dir=folder;
        File[] all=dir.listFiles();ArrayList<File> dirs=new ArrayList<>();if(all!=null)for(File f:all)if(f.isDirectory()&&f.canRead())dirs.add(f);
        Collections.sort(dirs,(a,b)->a.getName().compareToIgnoreCase(b.getName()));String[] names=new String[dirs.size()];for(int i=0;i<names.length;i++)names[i]=dirs.get(i).getName();
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(dir.getAbsolutePath()).setItems(names,(d,n)->browseFolder(dirs.get(n))).setPositiveButton("Use this folder",(d,w)->{prefs.edit().putString("defaultPath",dir.getAbsolutePath()).apply();drawPage();}).setNegativeButton("Cancel",null);
        if(dir.getParentFile()!=null)b.setNeutralButton("Up",(d,w)->browseFolder(dir.getParentFile()));b.show();
    }
    private void managePins(){ArrayList<String> pins=new ArrayList<>(prefs.getStringSet("pinned",Collections.emptySet()));Collections.sort(pins);new AlertDialog.Builder(this).setTitle("Tap a folder to unpin").setItems(pins.toArray(new String[0]),(d,n)->{Set<String> copy=new HashSet<>(pins);copy.remove(pins.get(n));prefs.edit().putStringSet("pinned",copy).apply();drawPage();}).setPositiveButton("Close",null).show();}
    private void pickColor(String title,String role){
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(dp(20),dp(12),dp(20),dp(8));
        View preview=new View(this);layout.addView(preview,new LinearLayout.LayoutParams(-1,dp(48)));
        EditText hex=new EditText(this);hex.setSingleLine(true);hex.setHint("#RRGGBB");layout.addView(hex);
        SeekBar[] sliders=new SeekBar[3];boolean[] updating={false};int[] value={theme.color(role)};
        for(int i=0;i<3;i++){TextView label=label(new String[]{"Red","Green","Blue"}[i],12);layout.addView(label);sliders[i]=new SeekBar(this);sliders[i].setMax(255);layout.addView(sliders[i]);}
        Runnable sync=()->{updating[0]=true;preview.setBackgroundColor(value[0]);hex.setText(String.format(Locale.ROOT,"#%06X",value[0]&0xffffff));sliders[0].setProgress(Color.red(value[0]));sliders[1].setProgress(Color.green(value[0]));sliders[2].setProgress(Color.blue(value[0]));updating[0]=false;};
        for(SeekBar slider:sliders)slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int progress,boolean user){if(user&&!updating[0]){value[0]=Color.rgb(sliders[0].getProgress(),sliders[1].getProgress(),sliders[2].getProgress());sync.run();}}});
        hex.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int start,int before,int count){if(updating[0]||!s.toString().matches("#?[0-9a-fA-F]{6}"))return;value[0]=Color.parseColor(s.charAt(0)=='#'?s.toString():"#"+s);updating[0]=true;preview.setBackgroundColor(value[0]);sliders[0].setProgress(Color.red(value[0]));sliders[1].setProgress(Color.green(value[0]));sliders[2].setProgress(Color.blue(value[0]));updating[0]=false;}public void afterTextChanged(Editable e){}});
        TextView paletteLabel=label("Presets and saved custom colors",12);layout.addView(paletteLabel);HorizontalScrollView paletteScroll=new HorizontalScrollView(this);LinearLayout palette=new LinearLayout(this);paletteScroll.addView(palette);layout.addView(paletteScroll);
        LinkedHashSet<String> colors=new LinkedHashSet<>(Arrays.asList("#4169E1","#008577","#B23A48","#8055A5","#D07800","#202124","#FFFFFF"));ArrayList<String> saved=new ArrayList<>(prefs.getStringSet("customColors",Collections.emptySet()));Collections.sort(saved);colors.addAll(saved);
        for(String color:colors){View swatch=new View(this);swatch.setBackgroundColor(Color.parseColor(color));swatch.setContentDescription(color);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(40),dp(40));lp.setMargins(dp(3),dp(8),dp(3),dp(8));palette.addView(swatch,lp);swatch.setOnClickListener(v->{value[0]=Color.parseColor(color);sync.run();});}
        sync.run();ScrollView scroll=new ScrollView(this);scroll.addView(layout);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("Cancel",null).setNeutralButton("Default",(d,w)->{prefs.edit().remove(theme.key(role)).apply();drawPage();}).setPositiveButton("Apply and save",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{if(!hex.getText().toString().matches("#?[0-9a-fA-F]{6}")){hex.setError("Enter six hexadecimal digits");return;}Set<String> custom=new HashSet<>(prefs.getStringSet("customColors",Collections.emptySet()));custom.add(String.format(Locale.ROOT,"#%06X",value[0]&0xffffff));prefs.edit().putInt(theme.key(role),value[0]).putStringSet("customColors",custom).apply();dialog.dismiss();drawPage();}));dialog.show();
    }
}
