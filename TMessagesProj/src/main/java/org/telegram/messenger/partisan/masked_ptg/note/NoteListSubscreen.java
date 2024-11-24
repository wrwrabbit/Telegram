package org.telegram.messenger.partisan.masked_ptg.note;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.partisan.masked_ptg.MaskedPtgConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.List;

class NoteListSubscreen extends FrameLayout {
    interface NoteListSubscreenDelegate {
        void onNoteClicked(int pos);
        void onNoteSelected(int pos);
        void onAddNote();
    }

    private final List<Note> notes;
    private final NoteListSubscreenDelegate delegate;

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private boolean tutorial;
    private View tutorialArrow;

    NoteListSubscreen(Context context, List<Note> notes, NoteListSubscreenDelegate delegate) {
        super(context);

        this.notes = notes;
        this.delegate = delegate;
        createFloatingButton();
        createTipArrow();
        createListView();
    }

    private void createFloatingButton() {
        Button floatingButton = new Button(getContext());
        floatingButton.setText("+");
        floatingButton.setTextColor(Color.BLACK);
        floatingButton.setTextSize(24);

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(getPrimaryColor());
        floatingButton.setBackground(Theme.AdaptiveRipple.filledCircle(shape, getPrimaryColor(), -1));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(56), dp(56));
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.setMargins(dp(16), dp(16), dp(16), dp(16));
        floatingButton.setLayoutParams(params);
        floatingButton.setOnClickListener(v -> {
            delegate.onAddNote();
        });
        addView(floatingButton);
    }

    private void createTipArrow() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(56), dp(56));
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.setMargins(dp(16), dp(16), dp(16) + dp(56), dp(16) + dp(56));

        tutorialArrow = new TutorialArrow(getContext(), -45, 0, dp(22));
        tutorialArrow.setLayoutParams(params);
        addView(tutorialArrow);
    }

    private void createListView() {
        listView = new RecyclerListView(getContext());
        listView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        listView.setVerticalScrollBarEnabled(false);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setGlowColor(getPrimaryColor());
        listView.setAdapter(listAdapter = new ListAdapter(getContext()));
        listView.setOnItemClickListener((view, position) -> delegate.onNoteClicked(position / 2));
        listView.setOnItemLongClickListener((view, position) -> {
            delegate.onNoteSelected(position / 2);
            return true;
        });
        listView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                outRect.left = dp(16);
                outRect.right = dp(16);
            }
        });
        addView(listView);
    }

    void notifyNoteEdited(int pos) {
        listAdapter.notifyItemChanged(pos * 2);
    }

    void notifyNoteAdded() {
        listAdapter.notifyItemInserted(notes.size() * 2 - 2);
        listAdapter.notifyItemInserted(notes.size() * 2 - 1);
    }

    void notifyNoteRemoved(int pos) {
        listAdapter.notifyItemRemoved(pos * 2);
        if (pos < notes.size()) {
            listAdapter.notifyItemInserted(pos * 2 + 1);
        }
    }

    void setTutorial(boolean tutorial) {
        this.tutorial = tutorial;
        tutorialArrow.setVisibility(tutorial ? View.VISIBLE : View.GONE);
    }

    private int getPrimaryColor() {
        return MaskedPtgConfig.getPrimaryColor(getContext());
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return getItemViewType(holder.getAdapterPosition()) == 0;
        }

        @Override
        public int getItemCount() {
            if (notes.isEmpty()) {
                return 0;
            } else {
                return notes.size() * 2 - 1;
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                default:
                    view = new NoteCell(context);
                    break;
                case 1:
                    view = new ShadowSectionCell(context);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    Note note = notes.get(position / 2);
                    NoteCell textCell = (NoteCell) holder.itemView;
                    textCell.setTitleAndDescription(note.title, note.description);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            return position % 2;
        }
    }
}
