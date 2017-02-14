package edu.polito.ConductiveCoffins.SurfinHUZZAH;
import android.util.Log;


import java.util.ArrayList;

/**
 * MIDI converter
 */

public class ArduinoMidiConverter {

    int keyInst;
    int windInst;
    int rootNote=-1;
    int octave=4;
    private int[] scale = new int[8];


    private boolean[] buttonsOld = new boolean[8];
    private boolean[] buttonsNew = {false,false,false,false,false,false,false,false};
    private int type;
    private int volume;
    private int proximity;
    private boolean gotData=false;

    private int[] notes = {60,62,64,65,67,69,71,72};

    private static final int[] MAJOR = {0,2,4,5,7,9,11,12};
    private static final int[] MINOR = {0,2,3,5,7,8,10,12};
    private static final int[] DORIAN = {0,2,3,5,7,9,10,12};
    private static final int[] LYDIAN = {0,2,4,6,7,9,11,12};
    private static final int[] MIXOLYDIAN = {0,2,4,5,7,9,10,12};

    //default constructor
    public ArduinoMidiConverter() {
        keyInst =-1;
        windInst =-1;
        this.setScale();
    }


    //Method to split input string

    public void retrieveFromWifi(String result)
    {
        String[] array = result.split("(?!^)");

        System.arraycopy(buttonsNew, 0, buttonsOld, 0, 8);

        for(int i=0; i<8; i++){
            buttonsNew[i] = (Integer.parseInt(array[i]) != 0);
        }

        if(Integer.parseInt(array[8])==0) type= keyInst;
        if(Integer.parseInt(array[8])==1) type= windInst;

        String volumeString="";
        for(int i=9; i<12; i++){ volumeString+=array[i];}
        volume = Integer.parseInt(volumeString);

        String proximityString="";
        for(int i=12; i<array.length; i++){ proximityString+=array[i];}
        proximity = Integer.parseInt(proximityString);

        gotData=true;
    }

    public int getInstrument(){

        if(gotData) {
            return type;
        }
        else {
            Log.e("ERROR","NO DATA FROM WIFI");
            return -1;}
    }

    public ArrayList<MidiNote> getNotes(){

        ArrayList<MidiNote> midiNotes = new ArrayList<MidiNote>();

        if(type== keyInst) //Keyboard
            for (int i=0; i<8; i++){
                if(buttonsOld[i] != buttonsNew[i]){
                    MidiNote m = new MidiNote(buttonsNew[i], notes[i], 90);
                    midiNotes.add(m);
                }
            }
        else if (type== windInst){ //Wind
            for (int i=0; i<8; i++){
                if(buttonsOld[i] != buttonsNew[i]){
                    MidiNote m = new MidiNote(buttonsNew[i], notes[i], 90);
                    midiNotes.add(m);
                }
            }
        }

        return midiNotes;
    }


    public int getVolume()
    {
        return volume;
    }

    public byte getProximityMSB()
    {
        return intToByteArray(proximity)[1];
    }
    public int getProximityLSB()
    {
        return intToByteArray(proximity)[0];
    }

    public static final byte[] intToByteArray(int value) {
        return new byte[]
                {
                        (byte)(value&0x7F),//LSB
                        (byte)((value&0x3F80)>>>7)};//MSB
    }

    public void setScale(){

        if(rootNote!=-1) {
            for (int i = 0; i < 8; i++) {
                notes[i] = rootNote + scale[i];
            }
        }
    }

    public void setOctave(boolean octavechange){
        if (octavechange){
            if(octave<10)
            octave += 1;
        }else {
            if(octave>0)
            octave -= 1;
        }
        setScale();
    }

    public int getOctave(){
        return octave;
    }

    public void chooseMyInstruments(String spinnerKeys, String spinnerWind){
        if (spinnerKeys.equals("Acoustic Grand Piano"))
            keyInst =0;
        if (spinnerKeys.equals("Accordion"))
            keyInst =21;
        if (spinnerKeys.equals("Vibraphone"))
            keyInst =11;
        if (spinnerKeys.equals("Xilophone"))
            keyInst =13;
        if (spinnerKeys.equals("Electric Piano"))
            keyInst =4;
        if (spinnerKeys.equals("Honky Tonk Piano"))
            keyInst =3;
        if (spinnerKeys.equals("Harpsichord"))
            keyInst =6;
        if (spinnerKeys.equals("Rock Organ"))
            keyInst =18;
        if (spinnerKeys.equals("Church Organ"))
            keyInst =19;
        if (spinnerKeys.equals("Marimba"))
            keyInst =12;

        if (spinnerWind.equals("Harmonica"))
            windInst =22;
        if (spinnerWind.equals("Trumpet"))
            windInst =56;
        if (spinnerWind.equals("Trombone"))
            windInst =57;
        if (spinnerWind.equals("Sax Tenor"))
            windInst =66;
        if (spinnerWind.equals("Choir"))
            windInst =52;
        if (spinnerWind.equals("French Horn"))
            windInst =60;
        if (spinnerWind.equals("Oboe"))
            windInst =68;
        if (spinnerWind.equals("Clarinet"))
            windInst =71;
        if (spinnerWind.equals("Ocarina"))
            windInst =79;
        if (spinnerWind.equals("Whistle"))
            windInst =78;
    }

    public void chooseMyNotes(String spinnerRootNote, String spinnerScale){

        if (spinnerRootNote.equals("C"))
            rootNote=12*octave;
        if (spinnerRootNote.equals("C#"))
            rootNote=12*octave+1;
        if (spinnerRootNote.equals("D"))
            rootNote=12*octave+2;
        if (spinnerRootNote.equals("D#"))
            rootNote=12*octave+3;
        if (spinnerRootNote.equals("E"))
            rootNote=12*octave+4;
        if (spinnerRootNote.equals("F"))
            rootNote=12*octave+5;
        if (spinnerRootNote.equals("F#"))
            rootNote=12*octave+6;
        if (spinnerRootNote.equals("G"))
            rootNote=12*octave+7;
        if (spinnerRootNote.equals("G#"))
            rootNote=12*octave+8;
        if (spinnerRootNote.equals("A"))
            rootNote=12*octave+9;
        if (spinnerRootNote.equals("A#"))
            rootNote=12*octave+10;
        if (spinnerRootNote.equals("B"))
            rootNote=12*octave+11;

        if (spinnerScale.equals("Major"))
            System.arraycopy(MAJOR, 0, scale, 0, 8);
        if (spinnerScale.equals("Minor"))
            System.arraycopy(MINOR, 0, scale, 0, 8);
        if (spinnerScale.equals("Dorian"))
            System.arraycopy(DORIAN, 0, scale, 0, 8);
        if (spinnerScale.equals("Lydian"))
            System.arraycopy(LYDIAN, 0, scale, 0, 8);
        if (spinnerScale.equals("Mixolydian"))
            System.arraycopy(MIXOLYDIAN, 0, scale, 0, 8);

        setScale();
    }
    public boolean isAllOff() {
        boolean k=true;
        for (int i=0;i<8;i++) {
            if (buttonsNew[i]) {
                k = false;
            }

        }
        return k;

    }

}
