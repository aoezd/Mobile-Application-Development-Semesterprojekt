package com.aoezdemir.todoapp.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import com.aoezdemir.todoapp.R;
import com.aoezdemir.todoapp.activity.adapter.ContactAdapter;
import com.aoezdemir.todoapp.crud.local.TodoDBHelper;
import com.aoezdemir.todoapp.crud.remote.ServiceFactory;
import com.aoezdemir.todoapp.model.Todo;
import com.aoezdemir.todoapp.utils.AlertDialogMaker;
import com.aoezdemir.todoapp.utils.ContactUtils;

import java.util.Calendar;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EditActivity extends AppCompatActivity {

    public final static String INTENT_KEY_TODO = "EDIT_KEY_TODO";
    public final static int REQUEST_PICK_CONTACTS = 0;
    public final static int REQUEST_PERMISSIONS = 1;

    private Todo todo;
    private long expiry;
    private EditText etEditTitle;
    private EditText etEditDescription;
    private Switch sEditDone;
    private Switch sEditFavourite;
    private Button bSaveTodo;
    private boolean isApiAccessible;
    private TodoDBHelper db;
    private ContactAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);
        todo = (Todo) getIntent().getSerializableExtra(INTENT_KEY_TODO);
        isApiAccessible = getIntent().getBooleanExtra(RouterEmptyActivity.INTENT_IS_WEB_API_ACCESSIBLE, false);
        db = new TodoDBHelper(this);
        adapter = new ContactAdapter(todo, true, getContentResolver(), this);
        loadTodoTitle();
        loadTodoExpiry();
        loadTodoDescription();
        loadTodoDone();
        loadTodoFavourite();
        loadTodoContacts();
        loadSaveButton();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_CONTACTS && resultCode == RESULT_OK && data != null && data.getData() != null) {
            todo.addContact(ContactUtils.getContactIdAndName(getContentResolver(), data.getData()));
            adapter.setContacts(todo.getContacts());
            adapter.notifyDataSetChanged();
            enableSaveButton();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            startActivityForResult(new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI), REQUEST_PICK_CONTACTS);
        }
    }

    private void updateTodoWithUIData() {
        todo.setName(etEditTitle.getText().toString());
        todo.setExpiry(expiry);
        todo.setDone(sEditDone.isChecked());
        todo.setDescription(etEditDescription.getText().toString());
        todo.setFavourite(sEditFavourite.isChecked());
    }

    private void loadTodoTitle() {
        etEditTitle = findViewById(R.id.etEditTitle);
        etEditTitle.setText(todo.getName());
        etEditTitle.setOnFocusChangeListener((View v, boolean hasFocus) -> {
            if (!hasFocus && !etEditTitle.getText().toString().equals(todo.getName())) {
                enableSaveButton();
            }
        });
    }

    private void loadTodoExpiry() {
        CalendarView cvEditExpiryDate = findViewById(R.id.cvEditExpiryDate);
        expiry = todo.getExpiry();
        cvEditExpiryDate.setDate(expiry);
        cvEditExpiryDate.setOnDateChangeListener((@NonNull CalendarView view, int year, int month, int dayOfMonth) -> {
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            expiry = calendar.getTime().getTime();
            enableSaveButton();
        });
    }

    private void loadTodoDescription() {
        etEditDescription = findViewById(R.id.etEditDescription);
        etEditDescription.setText(todo.getDescription());
        etEditDescription.setOnFocusChangeListener((View v, boolean hasFocus) -> {
            if (!hasFocus && !etEditDescription.getText().toString().equals(todo.getDescription())) {
                enableSaveButton();
            }
        });
    }

    private void loadTodoDone() {
        sEditDone = findViewById(R.id.sEditDone);
        sEditDone.setChecked(todo.isDone());
        sEditDone.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> enableSaveButton());
    }

    private void loadTodoFavourite() {
        sEditFavourite = findViewById(R.id.sEditFavourite);
        sEditFavourite.setChecked(todo.isFavourite());
        sEditFavourite.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> enableSaveButton());
    }

    public void enableSaveButton() {
        bSaveTodo.setEnabled(true);
        bSaveTodo.setBackgroundColor(getResources().getColor(R.color.colorAccent, null));
    }

    private void loadTodoContacts() {
        RecyclerView rvContacts = findViewById(R.id.rvEditContacts);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        rvContacts.setLayoutManager(linearLayoutManager);
        rvContacts.setAdapter(adapter);
        findViewById(R.id.ibEditAddContact).setOnClickListener((View v) -> {
            if (PackageManager.PERMISSION_GRANTED != checkSelfPermission(Manifest.permission.READ_CONTACTS)) {
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_PERMISSIONS);
                return;
            }
            startActivityForResult(new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI), REQUEST_PICK_CONTACTS);
        });
    }

    private void loadSaveButton() {
        bSaveTodo = findViewById(R.id.bSaveTodo);
        bSaveTodo.setEnabled(false);
        bSaveTodo.setOnClickListener((View v) -> {
            updateTodoWithUIData();
            if (todo.getName().isEmpty()) {
                AlertDialogMaker.makeNeutralOkAlertDialog(this, "No title set", "Please provide at least a title for the todo.");
            } else {
                boolean dbUpdateSucceeded = db.updateTodo(todo);
                if (dbUpdateSucceeded) {
                    if (isApiAccessible) {
                        ServiceFactory.getServiceTodo().updateTodo(todo.getId(), todo).enqueue(new Callback<Todo>() {
                            @Override
                            public void onResponse(@NonNull Call<Todo> call, @NonNull Response<Todo> response) {
                                if (!response.isSuccessful()) {
                                    Toast.makeText(getApplicationContext(), "Remote error: Failed to updateTodo Todo", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<Todo> call, @NonNull Throwable t) {
                                Toast.makeText(getApplicationContext(), t.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    Intent editTodoIntent = new Intent();
                    editTodoIntent.putExtra(INTENT_KEY_TODO, todo);
                    setResult(RESULT_OK, editTodoIntent);
                    finish();
                } else {
                    Toast.makeText(getApplicationContext(), "Local error: Failed to updateTodo Todo", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}