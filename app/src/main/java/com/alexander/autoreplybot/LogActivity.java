package com.alexander.autoreplybot;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alexander.autoreplybot.databinding.ActivityLogBinding;

import java.util.List;

/**
 * Shows the history of all auto-replies sent by the bot.
 */
public class LogActivity extends AppCompatActivity {

    private ActivityLogBinding binding;
    private AppPreferences prefs;
    private LogAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLogBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.log_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        prefs = new AppPreferences(this);

        List<LogEntry> entries = prefs.getLogEntries();
        adapter = new LogAdapter(entries);

        binding.recyclerLog.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerLog.setAdapter(adapter);

        updateEmptyState(entries);

        binding.btnClearLog.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.clear_log_title)
                    .setMessage(R.string.clear_log_message)
                    .setPositiveButton(R.string.clear, (d, w) -> {
                        prefs.clearLog();
                        adapter.updateData(prefs.getLogEntries());
                        updateEmptyState(prefs.getLogEntries());
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void updateEmptyState(List<LogEntry> entries) {
        if (entries.isEmpty()) {
            binding.tvEmptyLog.setVisibility(View.VISIBLE);
            binding.recyclerLog.setVisibility(View.GONE);
            binding.btnClearLog.setEnabled(false);
        } else {
            binding.tvEmptyLog.setVisibility(View.GONE);
            binding.recyclerLog.setVisibility(View.VISIBLE);
            binding.btnClearLog.setEnabled(true);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RecyclerView Adapter
    // ─────────────────────────────────────────────────────────────────────────

    static class LogAdapter extends RecyclerView.Adapter<LogAdapter.ViewHolder> {

        private List<LogEntry> data;

        LogAdapter(List<LogEntry> data) {
            this.data = data;
        }

        void updateData(List<LogEntry> newData) {
            this.data = newData;
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_log_entry, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            LogEntry entry = data.get(position);
            holder.tvContact.setText(entry.getContactName());
            holder.tvTime.setText(entry.getFormattedTime());
            holder.tvMessage.setText(entry.getMessage());
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvContact, tvTime, tvMessage;

            ViewHolder(View itemView) {
                super(itemView);
                tvContact = itemView.findViewById(R.id.tvLogContact);
                tvTime    = itemView.findViewById(R.id.tvLogTime);
                tvMessage = itemView.findViewById(R.id.tvLogMessage);
            }
        }
    }
}
