package com.cantal.getwell4;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.widget.*;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
import java.io.*;

public class MainActivity extends Activity {
  private static final int SCAN_REQUEST = 1001;
  private SQLiteDatabase db;
  private LinearLayout root;
  private long selectedPatient = -1;
  private String selectedName = "";
  private TextView status;

  static class Store extends SQLiteOpenHelper {
    Store(Activity a) { super(a, "getwell4.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
      db.execSQL("CREATE TABLE patients(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, reference TEXT)");
      db.execSQL("CREATE TABLE documents(id INTEGER PRIMARY KEY AUTOINCREMENT, patient_id INTEGER NOT NULL, filename TEXT NOT NULL, created_at TEXT DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(patient_id) REFERENCES patients(id))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int a, int b) {}
  }
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE);
    db = new Store(this).getWritableDatabase();
    home();
  }
  private TextView text(String value, int size) {
    TextView t = new TextView(this); t.setText(value); t.setTextSize(size);
    t.setTextColor(Color.rgb(28,52,65)); t.setPadding(0,12,0,12); return t;
  }
  private void base(String heading) {
    ScrollView scroll = new ScrollView(this);
    root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(32,32,32,40); root.setBackgroundColor(Color.rgb(247,250,252));
    scroll.addView(root); setContentView(scroll);
    root.addView(text(heading,26));
    status = text("",14); root.addView(status);
  }
  private void button(String label, Runnable action) {
    Button b = new Button(this); b.setText(label); b.setAllCaps(false);
    b.setOnClickListener(v -> action.run()); root.addView(b);
  }
  private void home() {
    selectedPatient = -1; base("GetWell 4.0");
    root.addView(text("Offline patient index and A4 document scanning prototype. Local device storage only.",15));
    root.addView(text("Prototype only: no login, encryption-at-rest, audit trail, backup, or clinical validation. Do NOT use real patient data.",14));
    button("+ Add test patient", () -> addPatient());
    Cursor c = db.rawQuery("SELECT id,name,reference FROM patients ORDER BY name",null);
    try { while(c.moveToNext()) {
      final long id=c.getLong(0); final String name=c.getString(1);
      button(name + " (" + c.getString(2) + ")", () -> patient(id,name));
    }} finally { c.close(); }
  }
  private void addPatient() {
    base("Add test patient");
    EditText name = new EditText(this); name.setHint("Test patient name"); root.addView(name);
    EditText ref = new EditText(this); ref.setHint("Reference (optional)"); root.addView(ref);
    button("Save", () -> {
      String n=name.getText().toString().trim();
      if(n.isEmpty()) { status.setText("Enter a name"); return; }
      android.content.ContentValues values = new android.content.ContentValues();
      values.put("name",n); values.put("reference",ref.getText().toString().trim());
      long id=db.insertOrThrow("patients",null,values); patient(id,n);
    });
    button("Back", () -> home());
  }
  private void patient(long id, String name) {
    selectedPatient=id; selectedName=name;
    base("Patient: "+name);
    root.addView(text("Scan paper records. Scanner supports automatic edge detection, cropping and multiple pages when Google Play services supports it.",15));
    button("Scan A4 paper → PDF", () -> scan());
    Cursor c=db.rawQuery("SELECT filename,created_at FROM documents WHERE patient_id=? ORDER BY id DESC",new String[]{String.valueOf(id)});
    try { while(c.moveToNext()) {
      final String file=c.getString(0);
      button("PDF • "+c.getString(1), () -> openPdf(file));
    }} finally {c.close();}
    button("Back to patients", () -> home());
  }
  private void scan() {
    if(selectedPatient<0) return;
    GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
      .setGalleryImportAllowed(false)
      .setPageLimit(30)
      .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
      .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
      .build();
    GmsDocumentScanning.getClient(options).getStartScanIntent(this)
      .addOnSuccessListener(sender -> {
        try { startIntentSenderForResult(sender,SCAN_REQUEST,null,0,0,0); }
        catch(Exception e) { status.setText("Unable to launch scanner: "+e.getMessage()); }
      })
      .addOnFailureListener(e -> status.setText("Scanner unavailable: "+e.getMessage()));
  }
  @Override protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request,result,data);
    if(request!=SCAN_REQUEST || result!=RESULT_OK || data==null || selectedPatient<0) return;
    GmsDocumentScanningResult scan=GmsDocumentScanningResult.fromActivityResultIntent(data);
    if(scan==null || scan.getPdf()==null) {status.setText("No PDF returned"); return;}
    Uri uri=scan.getPdf().getUri();
    File folder=new File(getFilesDir(),"patient_documents");
    if(!folder.exists() && !folder.mkdirs()) {status.setText("Storage error"); return;}
    String filename="patient_"+selectedPatient+"_"+System.currentTimeMillis()+".pdf";
    File dest=new File(folder,filename);
    try(InputStream in=getContentResolver().openInputStream(uri); OutputStream out=new FileOutputStream(dest)) {
      if(in==null) throw new IOException("No scanner data");
      byte[] buf=new byte[8192]; int count;
      while((count=in.read(buf))!=-1) out.write(buf,0,count);
      android.content.ContentValues v=new android.content.ContentValues();
      v.put("patient_id",selectedPatient);v.put("filename",filename);
      db.insertOrThrow("documents",null,v);
      patient(selectedPatient,selectedName);
    } catch(Exception e) {dest.delete();status.setText("Save failed: "+e.getMessage());}
  }
  private void openPdf(String filename) {
    File file=new File(new File(getFilesDir(),"patient_documents"),filename);
    try {
      // PDF remains in app-private storage; no implicit sharing to other applications.
      if(!file.isFile()) throw new IOException("PDF not found");
      android.graphics.pdf.PdfRenderer renderer=new android.graphics.pdf.PdfRenderer(
        android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY));
      base("Document preview");
      root.addView(text(filename+" • "+renderer.getPageCount()+" page(s)",15));
      for(int i=0;i<renderer.getPageCount();i++) {
        android.graphics.pdf.PdfRenderer.Page page=renderer.openPage(i);
        int w=720; int h=Math.max(1,Math.round((float)page.getHeight()*w/page.getWidth()));
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE); page.render(bitmap,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);root.addView(image);
        page.close();
      }
      renderer.close();
      button("Back", () -> patient(selectedPatient,selectedName));
    } catch(Exception e) {status.setText("Preview error: "+e.getMessage());}
  }
}