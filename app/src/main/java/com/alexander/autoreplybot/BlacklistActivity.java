package com.alexander.autoreplybot;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alexander.autoreplybot.databinding.ActivityBlacklistBinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lets the user pick contacts to exclude from auto-replies (blacklist).
 * Contacts are loaded from the phone's address book.
 * The user can also type a name manually.
 */
public class BlacklistActivity extends AppCompatActivity {

    private static final int REQ_CONTACTS = 200;

    private ActivityBlacklistBinding binding;
    private AppPreferences prefs;
    private ContactAdapter adapter;
    private List<ContactItem> allContacts = new ArrayList<>();
    private List<ContactItem> filteredContacts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBlacklistBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.blacklist_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        prefs = new AppPreferences(this);

        adapter = new ContactAdapter(filteredContacts, prefs.getBlacklist(), name -> {
            // Toggle blacklist
            if (prefs.isBlacklisted(name)) {
                prefs.removeFromBlacklist(name);
            } else {
                prefs.addToBlacklist(name);
            }
            updateBlacklistCount();
        });

        binding.recyclerContacts.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerContacts.setAdapter(adapter);

        // Search filter
        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterContacts(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Add manual entry
        binding.btnAddManual.setOnClickListener(v -> {
            String name = binding.editSearch.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, R.string.enter_name_hint, Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.addToBlacklist(name);
            // Add to list if not already there
            boolean found = false;
            for (ContactItem c : allContacts) {
                if (c.name.equalsIgnoreCase(name)) { found = true; break; }
            }
            if (!found) {
                allContacts.add(0, new ContactItem(name));
            }
            filterContacts(binding.editSearch.getText().toString());
            binding.editSearch.setText("");
            Toast.makeText(this, getString(R.string.added_to_blacklist, name), Toast.LENGTH_SHORT).show();
            updateBlacklistCount();
        });

        loadContacts();
        updateBlacklistCount();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void loadContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
            return;
        }
        readContactsFromPhone();
    }

    private void readContactsFromPhone() {
        allContacts.clear();
        Set<String> seen = new HashSet<>();

        Cursor cursor = getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                new String[]{ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},
                null, null,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC");

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty() && seen.add(name)) {
                    allContacts.add(new ContactItem(name));
                }
            }
            cursor.close();
        }

        // Also add manually blacklisted names that aren't in contacts
        for (String bl : prefs.getBlacklist()) {
            if (seen.add(bl)) {
                allContacts.add(0, new ContactItem(bl));
            }
        }

        filterContacts("");
    }

    private void filterContacts(String query) {
        filteredContacts.clear();
        String lower = query.toLowerCase().trim();
        for (ContactItem c : allContacts) {
            if (lower.isEmpty() || c.name.toLowerCase().contains(lower)) {
                filteredContacts.add(c);
            }
        }
        adapter.updateBlacklist(prefs.getBlacklist());
        adapter.notifyDataSetChanged();
    }

    private void updateBlacklistCount() {
        int count = prefs.getBlacklist().size();
        binding.tvBlacklistCount.setText(getString(R.string.blacklist_count, count));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CONTACTS
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            readContactsFromPhone();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data model
    // ─────────────────────────────────────────────────────────────────────────

    static class ContactItem {
        String name;
        ContactItem(String name) { this.name = name; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Adapter
    // ─────────────────────────────────────────────────────────────────────────

    static class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ViewHolder> {

        interface OnToggleListener {
            void onToggle(String name);
        }

        private final List<ContactItem> data;
        private Set<String> blacklist;
        private final OnToggleListener listener;

        ContactAdapter(List<ContactItem> data, Set<String> blacklist, OnToggleListener listener) {
            this.data = data;
            this.blacklist = new HashSet<>(blacklist);
            this.listener = listener;
        }

        void updateBlacklist(Set<String> newBlacklist) {
            this.blacklist = new HashSet<>(newBlacklist);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_contact, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ContactItem item = data.get(position);
            holder.tvName.setText(item.name);
            holder.checkBox.setChecked(blacklist.contains(item.name));

            holder.itemView.setOnClickListener(v -> {
                listener.onToggle(item.name);
                if (blacklist.contains(item.name)) {
                    blacklist.remove(item.name);
                } else {
                    blacklist.add(item.name);
                }
                notifyItemChanged(position);
            });
            holder.checkBox.setOnClickListener(v -> {
                listener.onToggle(item.name);
                if (blacklist.contains(item.name)) {
                    blacklist.remove(item.name);
                } else {
                    blacklist.add(item.name);
                }
                notifyItemChanged(position);
            });
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            CheckBox checkBox;

            ViewHolder(View itemView) {
                super(itemView);
                tvName   = itemView.findViewById(R.id.tvContactName);
                checkBox = itemView.findViewById(R.id.checkBoxBlacklist);
            }
        }
    }
}
