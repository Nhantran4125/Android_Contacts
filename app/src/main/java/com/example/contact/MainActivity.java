package com.example.contact;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileUtils;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileReader;


import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    public static final int REQUEST_CODE = 123;
    private static final int REQUEST_CODE_FILECHOOSER = 2511;
    private static final String LOG_TAG = "Example";
    public String pathImport;

    RecyclerView recyclerView;
    Button btnExport, btnChooseFile, btnImport;
    TextView txtFilePath;
    Intent intentChooseFile;


    ArrayList<ContactModel> arrayList = new ArrayList<ContactModel>();
    ContactAdapter adapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        grantPermission();
        getContactList();

        //nút export CSV
        btnExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportCSV();
            }
        });

        //nút chọn file
        btnChooseFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                intentChooseFile = new Intent(Intent.ACTION_GET_CONTENT);
                intentChooseFile.setType("*/*");
                startActivityForResult(intentChooseFile, REQUEST_CODE_FILECHOOSER);
            }
        });

        //nút import CSV
        btnImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(txtFilePath.getText() != "")
                {
                    alertImport();
                }
                else
                {
                    Toast.makeText(getApplicationContext(), "Chọn file trước khi import", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode)
        {
            case REQUEST_CODE_FILECHOOSER:
                if(resultCode == RESULT_OK)
                {
                    //String path = getPath(data.getData());
                    String path = getPath(data.getData()); //chuyển Uri thành file path
                    pathImport = path;
                    txtFilePath.setText(path);
                }
                break;
        }
    }

    //khởi tạo các đối tượng UI
    public void init()
    {
        recyclerView = findViewById(R.id.recyle_view);
        btnExport = findViewById(R.id.btnExport);
        btnImport = findViewById(R.id.btnImport);
        btnChooseFile = findViewById(R.id.btnChooseFile);
        txtFilePath = findViewById(R.id.txtChosenFile);
    }

    //Hàm chuyển Uri thành file path
    public String getPath(Uri uri)
    {
        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor == null) return null;
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String s=cursor.getString(column_index);
        cursor.close();
        return s;
    }


    //Lấy danh sách từ danh bạ
    private void getContactList()
    {
        Uri uri = ContactsContract.Contacts.CONTENT_URI;
        String sort = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC";
        Cursor cursor = getContentResolver().query(
                uri, null, null, null, sort);
        if(cursor.getCount() > 0)
        {
            while (cursor.moveToNext())
            {
                String id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                Uri uriPhone = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
                String selection = ContactsContract.CommonDataKinds.Phone.CONTACT_ID+" =?";
                Cursor phoneCursor = getContentResolver().query(uriPhone, null, selection, new String[]{id}, null);
                if(phoneCursor.moveToNext())
                {
                    String number = phoneCursor.getString(phoneCursor.getColumnIndex(
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    ));
                    ContactModel contactModel = new ContactModel(name, number);
                    arrayList.add(contactModel);
                    phoneCursor.close();
                }

            }
            cursor.close();
        }
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ContactAdapter(this, arrayList);
        recyclerView.setAdapter(adapter);

    }

    //Hàm hỏi trước khi import file CSV
    private void alertImport()
    {
        AlertDialog.Builder builder= new AlertDialog.Builder(this);
                builder.setTitle("Xác nhận?")
                .setMessage("Bạn có muốn Import từ file này?")
                .setPositiveButton("Có", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                            File csvfile = new File(txtFilePath.getText().toString());
                            try {
                                importCSV(csvfile);

                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                    }
                })
                .setNegativeButton("Không", null)
                .show();
    }


    //Hàm import contact từ file CSV
    private void importCSV(File csvfile) throws IOException {
        BufferedReader reader;
        CSVReader csvReader;
        String[] nextLine;
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                FileInputStream fis = new FileInputStream(csvfile);
                reader = new BufferedReader(new InputStreamReader(fis, Charset.forName("UTF-8")));
                csvReader = new CSVReader(reader);

                while((nextLine = csvReader.readNext()) != null)
                {
                    if(nextLine.length != 2)
                        continue;
                    ContactModel item = new ContactModel(nextLine[0], nextLine[1]);
//                    item.setName(nextLine[0]);
//                    item.setNumber(nextLine[1]);
                    addContact(item);
                    arrayList.add(item);
                    adapter.notifyDataSetChanged();
                }
                txtFilePath.setText(""); //xóa đường dẫn sau khi import xong
                Toast.makeText(getApplicationContext(), "Import thành công", Toast.LENGTH_SHORT).show();
            }

        }
        catch (IOException e)
        {
            Toast.makeText(this, e.getCause().toString() + ": " + e.getMessage().toString(), Toast.LENGTH_SHORT).show();
        }
    }

    //Tạo contact mới
    private void addContact(ContactModel contactModel)
    {
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        int rawContactInsertIndex = ops.size();

        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build());
        // NAME ------------------------------------------------------------------------------------------//
        ops.add(ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,rawContactInsertIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contactModel.getName()) // Name of the person
                .build());
        // MOBILE PHONE ------------------------------------------------------------------------------------------//
        ops.add(ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(
                        ContactsContract.Data.RAW_CONTACT_ID,   rawContactInsertIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, contactModel.getNumber()) // Number of the person
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build()); // Type of mobile number
        try
        {
            ContentProviderResult[] res = getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        }
        catch (RemoteException e)
        {
            // error
            Toast.makeText(this, "Exception: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        catch (OperationApplicationException e)
        {
            // error
            Toast.makeText(this, "Exception: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    //Hàm export ra file CSV
    private void exportCSV()
    {
        try{

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                File file = new File(path, "dssdt.csv"); // export ra file này, trong folder Documents
                CSVWriter csvWriter = new CSVWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
//                String name = file.getAbsolutePath();
                for(ContactModel contact : arrayList)
                {
                    String [] line = new String[]{contact.getName(), contact.getNumber()};
                    csvWriter.writeNext(line);
                }
                csvWriter.close();
                Toast.makeText(this, "Export thành công", Toast.LENGTH_SHORT).show();
            }

        }
        catch (IOException e)
        {
            Toast.makeText(this, e.getCause().toString() + ": " + e.getMessage().toString(), Toast.LENGTH_SHORT).show();
        }

    }

    //gán quyền
    private void grantPermission()
    {
        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_CONTACTS) +
                ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_CONTACTS)+
                ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) +
                ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        {
            if(ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.READ_CONTACTS) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.WRITE_CONTACTS) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE))
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Cấp quyền");
                builder.setMessage("Danh bạ, Đọc và ghi bộ nhớ");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String []
                                        {       Manifest.permission.READ_CONTACTS,
                                                Manifest.permission.WRITE_CONTACTS,
                                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                Manifest.permission.READ_EXTERNAL_STORAGE
                                        }, REQUEST_CODE
                        );
                    }
                });

                builder.setNegativeButton("Cancel", null);
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
            }
            else
            {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String []
                                {       Manifest.permission.READ_CONTACTS,
                                        Manifest.permission.WRITE_CONTACTS,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                        Manifest.permission.READ_EXTERNAL_STORAGE
                                }, REQUEST_CODE
                );
            }
        }

    }
}