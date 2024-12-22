package org.telegram.messenger.partisan.masked_ptg.note;

import android.content.Context;
import android.content.SharedPreferences;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.PartisanLog;

import java.util.ArrayList;
import java.util.List;

class NoteStorage {
    public static List<Note> notes;

    private static class NoteListWrapper {
        public List<Note> notes;
        public NoteListWrapper(List<Note> notes) {
            this.notes = notes;
        }
        public NoteListWrapper() {}
    }

    private static final String PREFERENCES_KEY = "MaskedPtg_NoteList";

    public static void loadNotes() {
        notes = null;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
        if (preferences.contains(PREFERENCES_KEY)) {
            try {
                notes = SharedConfig.fromJson(preferences.getString(PREFERENCES_KEY, null), NoteListWrapper.class).notes;
            } catch (JsonProcessingException e) {
                PartisanLog.handleException(e);
            }
        }
        if (notes == null) {
            notes = getExampleNotes();
        }
    }

    public static void saveNotes() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        try {
            editor.putString(PREFERENCES_KEY, SharedConfig.toJson(new NoteListWrapper(notes)));
        } catch (JsonProcessingException e) {
            PartisanLog.handleException(e);
        }
        editor.apply();
    }

    private static List<Note> getExampleNotes() {
        List<Note> notes = new ArrayList<>();
        notes.add(new Note("Meeting Notes - Team Collaboration", "Discussed project timelines, assigned tasks, and set deadlines for next week's deliverables."));
        notes.add(new Note("Recipe - Spaghetti Carbonara", "Ingredients - spaghetti, eggs, pancetta, Parmesan cheese, black pepper. Steps - cook spaghetti, fry pancetta, mix eggs and cheese, combine all ingredients."));
        notes.add(new Note("Book Summary - \"The Alchemist\" by Paulo Coelho", "Follows the journey of a shepherd named Santiago as he seeks his Personal Legend. Themes include destiny, following one's dreams, and the importance of listening to one's heart."));
        return notes;
    }
}
