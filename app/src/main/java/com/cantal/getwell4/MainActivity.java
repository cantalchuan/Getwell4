package com.cantal.getwell4;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
public class MainActivity extends Activity {
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL); root.setPadding(36,48,36,36); root.setBackgroundColor(Color.rgb(246,249,252));
    TextView title = new TextView(this); title.setText("GetWell 4.0"); title.setTextSize(30); title.setTextColor(Color.rgb(15,65,91)); root.addView(title);
    TextView subtitle = new TextView(this); subtitle.setText("Clinic Management • Starter Build"); subtitle.setTextSize(16); root.addView(subtitle);
    TextView note = new TextView(this); note.setText("\nThis is an initial installation test, not a production patient-record system.\n\nPlanned: patient profiles, A4 camera scan, auto-crop, multipage PDF, secure attachments.\n\nDo not enter real patient information in this version."); note.setTextSize(17); root.addView(note);
    Button btn = new Button(this); btn.setText("Check installation"); btn.setOnClickListener(v -> Toast.makeText(this,"GetWell 4.0 starter is running",Toast.LENGTH_LONG).show()); root.addView(btn);
    setContentView(root);
  }
}