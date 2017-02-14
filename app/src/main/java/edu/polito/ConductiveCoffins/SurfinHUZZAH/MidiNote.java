package edu.polito.ConductiveCoffins.SurfinHUZZAH;

/**
 * Structure of a MIDI note
 */

public class MidiNote {
    private int channel;
    private int pitch;
    private int velocity;


    public MidiNote(boolean c, int p, int v) {
        if(c){channel=0x90;} else {channel=0x80;} //true=NoteON, false=NoteOff
        pitch=p;
        velocity=v;
    }

    public int getChannel(){
        return channel;
    }

    public int getPitch(){
        return pitch;
    }

    public int getVelocity(){
        return velocity;
    }
}
