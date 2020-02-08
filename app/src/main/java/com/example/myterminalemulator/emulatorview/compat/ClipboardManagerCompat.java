package com.example.myterminalemulator.emulatorview.compat;

public interface ClipboardManagerCompat {
	CharSequence getText();

	boolean hasText();

    void setText(CharSequence text);
}
