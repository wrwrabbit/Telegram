package org.telegram.messenger.partisan.masked_ptg.note;

class Note {
    String title;
    String description;

    Note() {}

    Note(Note other) {
        this(other.title, other.description);
    }

    Note(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
