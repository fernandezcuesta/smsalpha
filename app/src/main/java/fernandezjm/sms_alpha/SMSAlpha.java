package fernandezjm.sms_alpha;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.PhoneNumberUtils;
import android.util.SparseIntArray;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.widget.Switch;
import android.text.InputType;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Random;


public class SMSAlpha extends AppCompatActivity {
/*
    SMS-SUBMIT fields

    Byte 1:     00100001  (0x21)
        TP-MTI: 01 (SMS-SUBMIT)
        TP-RD
        TP-VPF
        TP-SRR: 1 (Status report request)
        TP-UDHI
        TP-RP
    Byte 2:
        TP-MR   01 (message reference)
    Bytes 3 - 4+N: (N: length of Destination Address)
        TP-DA
    Byte 5+N:
        TP-PID: 0x00
    Byte 6+N:
        TP-DCS: 0x00  (data coding scheme, 7bit)
    Byte 7+N:
        TP-UDL (user data length)
    Bytes 8+N - end:
        TP-UD (user data)
 */

    public static final byte GSM_EXTENDED_ESCAPE = 0x1B;
    public static final byte ALPHANUMERIC_TOA = 0x68;
    public static final int MAX_USER_DATA_BYTES = 140;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smsalpha);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage(view);
            }
        });
        Switch toggle = (Switch) findViewById(R.id.switchalpha);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                EditText laDestination = (EditText) findViewById(R.id.la_destination);
                if (isChecked) {
                    laDestination.setInputType(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                    laDestination.setText("");
                    laDestination.setHint(R.string.edit_lanumber);
                } else {
                    laDestination.setInputType(InputType.TYPE_CLASS_PHONE);
                    laDestination.setText("");
                    laDestination.setHint(R.string.numeric_lanumber);
                }
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_smsalpha, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /** Called when the user clicks the Send button */
    public void sendMessage(View view) {
        SmsManager smsmanager = SmsManager.getDefault();

        EditText editText = (EditText) findViewById(R.id.edit_message);
        EditText laDestination = (EditText) findViewById(R.id.la_destination);

        String textContent = editText.getText().toString();
        if (textContent.isEmpty()){
            // Try to send the edit_message hint instead of an empty message
            textContent = getResources().getString(R.string.edit_message);
        }
        ArrayList<String> messages = smsmanager.divideMessage(textContent);
        int messageCount = messages.size();

        if (messageCount == 0) {
            // Try to send a random 160-char message if even the hint was empty (should never occur)
            char[] chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
            StringBuilder sb = new StringBuilder();
            Random random = new Random();
            for (int i = 0; i < 160; i++) {
                char c = chars[random.nextInt(chars.length)];
                sb.append(c);
            }
            messages.add(0, sb.toString());
        }

        String la_number = laDestination.getText().toString();
        boolean isChecked = ((Switch) findViewById(R.id.switchalpha)).isChecked();

        if (la_number.isEmpty()) {
            // Send it to the la_destination hint in case nothing given.
            la_number = (isChecked ?
                         getResources().getString(R.string.edit_lanumber) :
                         getResources().getString(R.string.numeric_lanumber)
            );
        }

        byte [] destinationAddress;


        if (isChecked) {
            destinationAddress = stringToGsm7BitPacked(la_number, 0);
        }
        else{
            destinationAddress = PhoneNumberUtils.networkPortionToCalledPartyBCDWithLength(
                    la_number);
        }

        for (int i = 0; i < messageCount; i++) {
            byte[] sms_content = stringToGsm7BitPacked(messages.get(i), 0);
            ByteArrayOutputStream bo = getSubmitPduHead(
                    destinationAddress,
                    true,  // TODO: cambiar con un setting
                    isChecked);
            bo.write(sms_content, 0, sms_content.length); // TP-UDL and TP-UD
        smsmanager.injectSmsPdu(bo.toByteArray(), "3gpp", null);
        }

        byte[] sms_content = stringToGsm7BitPacked(textContent, 0);
        String ms7b = bytesToHex(sms_content);
        String destino = bytesToHex(destinationAddress);

        // Mensaje de confirmación
        Snackbar.make(view, destino.concat(" > enviando mensaje: ".concat(ms7b)), Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }

    private static ByteArrayOutputStream getSubmitPduHead(
            byte [] destinationAddress, boolean statusReportRequested, boolean alphanumericAddress)
    {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(
                MAX_USER_DATA_BYTES + 40);

        bo.write(0x01 | (statusReportRequested ? 0x20 : 0x00)); // SUBMIT-PDU MTU + SRR
        bo.write(0x01); // TP-MR
        bo.write(destinationAddress, 0, 1);  // Length from CalledPartyBCDWithLength
        bo.write((alphanumericAddress ? 0x68 : 0x81)); // TOA
        bo.write(destinationAddress, 1, destinationAddress[0]); // CalledPartyBCD (w/o length)
        bo.write(0x00); // TP-PID
        bo.write(0x00); // TP-DCS
        return bo;
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] stringToGsm7BitPacked(String data, int startingSeptetOffset)
    {
        int dataLen = data.length();
        int septetCount = countGsmSeptetsUsingTables(data);
        septetCount += startingSeptetOffset;
        int byteCount = ((septetCount * 7) + 7) / 8;
        byte[] ret = new byte[byteCount + 1];  // Include space for one byte length prefix.

        int tableLen = GSM7bitTable.length();

        SparseIntArray charToGsmTable = new SparseIntArray(tableLen);
        for (int j = 0; j < tableLen; j++) {
            char c = GSM7bitTable.charAt(j);
            charToGsmTable.put(c, j);
        }

        for (int i = 0, septets = startingSeptetOffset, bitOffset = startingSeptetOffset * 7;
             i < dataLen && septets < septetCount;
             i++, bitOffset += 7) {
            char c = data.charAt(i);
            int v = charToGsmTable.get(c, -1);
            if (v == -1) {
                v = charToGsmTable.get(' ', ' ');   // should return ASCII space
            }
            packSmsChar(ret, bitOffset, v);
            septets++;
        }
        ret[0] = (byte) (septetCount);  // Validated by check above.
        return ret;
    }

    private static void packSmsChar(byte[] packedChars, int bitOffset, int value) {
        int byteOffset = bitOffset / 8;
        int shift = bitOffset % 8;
        packedChars[++byteOffset] |= value << shift;
        if (shift > 1) {
            packedChars[++byteOffset] = (byte)(value >> (8 - shift));
        }
    }

    private static final String GSM7bitTable =
        /* 3GPP TS 23.038 V9.1.1 section 6.2.1 - GSM 7 bit Default Alphabet
         01.....23.....4.....5.....6.....7.....8.....9.....A.B.....C.....D.E.....F.....0.....1 */
            "@\u00a3$\u00a5\u00e8\u00e9\u00f9\u00ec\u00f2\u00c7\n\u00d8\u00f8\r\u00c5\u00e5\u0394_"
                    // 2.....3.....4.....5.....6.....7.....8.....9.....A.....B.....C.....D.....E.....
                    + "\u03a6\u0393\u039b\u03a9\u03a0\u03a8\u03a3\u0398\u039e\uffff\u00c6\u00e6\u00df"
                    // F.....012.34.....56789ABCDEF0123456789ABCDEF0.....123456789ABCDEF0123456789A
                    + "\u00c9 !\"#\u00a4%&'()*+,-./0123456789:;<=>?\u00a1ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                    // B.....C.....D.....E.....F.....0.....123456789ABCDEF0123456789AB.....C.....D.....
                    + "\u00c4\u00d6\u00d1\u00dc\u00a7\u00bfabcdefghijklmnopqrstuvwxyz\u00e4\u00f6\u00f1"
                    // E.....F.....
                    + "\u00fc\u00e0";

    public static int countGsmSeptetsUsingTables(CharSequence s) {
        int count = 0;
        int sz = s.length();

        for (int i = 0; i < sz; i++) {
            char c = s.charAt(i);
            if (c == GSM_EXTENDED_ESCAPE) {
                // countGsmSeptets() string contains Escape character, skipping
                continue;
            }
            count++;
        }
        return count;
    }


}
