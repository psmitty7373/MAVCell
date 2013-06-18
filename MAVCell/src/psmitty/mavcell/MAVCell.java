package psmitty.mavcell;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.PriorityBlockingQueue;

import jp.ksksue.driver.serial.FTDriver;
import com.psmitty.mavcell.R;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.hardware.usb.UsbManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MAVCell extends Activity {

    FTDriver mSerial;
    String TAG = "MAVCell";
    private static final String ACTION_USB_PERMISSION = "ps.mavcell.USB_PERMISSION";
    Button btnBegin,btnRead,btnEnd;
    EditText portNum, baudRate, serverIP;
    TextView tvMonitor;
    CheckBox serverMode, debugOn;
    private int port, baud, mSerialBaud;
    private PriorityBlockingQueue<String> outQueue = new PriorityBlockingQueue<String>(100);
    private PriorityBlockingQueue<String> inQueue = new PriorityBlockingQueue<String>(100);
    public boolean mStop=false;
    private boolean isServer=true;
    private boolean connected=false;
    private boolean debugging=false;
    private Handler handler = new Handler();
    private Thread fst, spt, mont;
    private PowerManager pm;
    private PowerManager.WakeLock wl;
    private WifiManager wm;
    private WifiManager.WifiLock wifil;
    private SharedPreferences pref;
    private Editor editor;
    private String serverIPAddr;
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) 
    {
        super.onConfigurationChanged(newConfig);
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.i("MAVCell", "!!!!!!!!***********CREATE!!!************!!!!!!!!!!!!!!");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_psmitty_mavcell);
        // WAKE LOCKS - WIFI = FULL, CPU = PARTIAL
        wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifil = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "MAVCell");
        wifil.acquire();
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MAVCell");
        wl.acquire();
        // INITIALIZE VIEW ELEMENTS
        btnBegin = (Button) findViewById(R.id.btnBegin);
        btnRead = (Button) findViewById(R.id.btnRead);
        btnEnd = (Button) findViewById(R.id.btnEnd);
        btnRead.setEnabled(false);
        btnEnd.setEnabled(false);
        tvMonitor = (TextView) findViewById(R.id.tvMonitor);
        portNum = (EditText)findViewById(R.id.portNum);
        baudRate = (EditText)findViewById(R.id.baudRate);
        serverIP = (EditText)findViewById(R.id.serverIP);
        serverMode = (CheckBox)findViewById(R.id.serverMode);
        debugOn = (CheckBox)findViewById(R.id.debugOn);
        // SAVED PREFERENCES
        pref = getApplicationContext().getSharedPreferences("com.psmitty.mavcell", MODE_PRIVATE);
        editor = pref.edit();
        port = pref.getInt("port", 8080);
        baud = pref.getInt("baud", 57600);
        isServer = pref.getBoolean("isServer", true);
        serverMode.setChecked(isServer);
        serverIPAddr = pref.getString("serverIP", "192.168.1.20");
        serverIP.setText(serverIPAddr);
        // INITIALIZE SERIAL OTG
        mSerial = new FTDriver((UsbManager)getSystemService(Context.USB_SERVICE));
        PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        mSerial.setPermissionIntent(permissionIntent);
        // CHECKBOX EVENT HANDLERS
        serverMode.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	    	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	    		if (isChecked) {
	    			isServer = true;
	    			serverIP.setEnabled(false);
	    		}
	    		else {
	    			isServer = false;
	    			serverIP.setEnabled(true);
	    		}
	    		editor.putBoolean("isServer", isServer);
	    		editor.commit();
	    	}
        });
        if (isServer)
        	serverIP.setEnabled(false);
        else
        	serverIP.setEnabled(true);
        debugOn.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	    	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	    		debugging = isChecked;
	    	}
        });
        // TEXTVIEW EVENT HANDLERS
        portNum.addTextChangedListener(new TextWatcher(){
            @Override
            public void afterTextChanged(Editable s) {
            	if (s.length() == 0)
            		port = 0;
            	else
            		port = Integer.parseInt(s.toString());
            	editor.putInt("port", port);
            	editor.commit();
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
        baudRate.addTextChangedListener(new TextWatcher(){
            @Override
            public void afterTextChanged(Editable s) {
            	if (s.length() == 0)
            		baud = 0;
            	else
            		baud = Integer.parseInt(s.toString());
            	editor.putInt("baud", baud);
            	editor.commit();
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
        serverIP.addTextChangedListener(new TextWatcher(){
            @Override
            public void afterTextChanged(Editable s) {
            	serverIPAddr = s.toString();
            	editor.putString("serverIP", serverIPAddr);
            	editor.commit();
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
        baudRate.setText(Integer.toString(baud));
        portNum.setText(Integer.toString(port));
    }
    
    // PREPEND STR TO A TEXTVIEW, LIMIT TO 1000CHARS
    public void appendToTextView(final String str) {
    	runOnUiThread(new Runnable() {
    	     public void run() {
		    	String tempStr = "";
		    	tempStr = (String) tvMonitor.getText();
		    	tempStr = str + tempStr;
		    	if (tempStr.length() > 1000)
		    		tempStr = tempStr.substring(0, 999);
		    	tvMonitor.setText(tempStr);
    	     }
    	});
    }
	
    // CONVERT RAW STR TO HEX STR
	public static String toHex(byte[] bytes) {
	    BigInteger bi = new BigInteger(1, bytes);
	    return String.format("%0" + (bytes.length << 1) + "X", bi);
	}
	
	// SERIAL PORT THREAD
	public class SerialPortThread implements Runnable {
		@Override
		public void run() {
			try {
				Log.i("MAVCell", "!!!!!!!!***********SPT!!!************!!!!!!!!!!!!!!");
				int len;
				String tempStr = "";
				byte[] rbuf = new byte[4096];
				while(!mStop)
				{
					if (inQueue.size() > 0) {
	        			for (int i=0; i < inQueue.size(); i++) {
		        			String temp = inQueue.remove();
		        			byte[] byteArray = Base64.decode(temp, Base64.DEFAULT);
		        			mSerial.write(byteArray);
	        			}
					}
					len = mSerial.read(rbuf);
					if(len > 0) {
						byte[] nbuf = new byte[len];
						for(int i=0; i<len; ++i) {
							nbuf[i] = rbuf[i];
						}
						String decoded = Base64.encodeToString(nbuf, Base64.DEFAULT);
						outQueue.add(decoded);
						if (debugging) {
							tempStr = toHex(nbuf);
							appendToTextView(tempStr + ":" + outQueue.size() + "\r\n");
						}
						Thread.sleep(10);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				appendToTextView(e.toString());
			}
		}
	};
	
	// MASTER THREAD MONITOR
	public class MonitorThread implements Runnable {
		public void run() {
			try {
				// START TCP AND SERIAL THREADS
	    		fst.start();
	        	spt.start();
				while (!mStop) {
					if (!spt.isAlive()) {
						appendToTextView("Serial thread stopped! Restarting.\r\n");
						spt = new Thread(new ServerThread());
						spt.start();
					}
					if (!fst.isAlive()) {
						appendToTextView("TCP/IP thread stopped! Restarting.\r\n");
						fst = new Thread(new ServerThread());
						fst.start();
					}
					Thread.sleep(100);
				}
			} catch (Exception e) {
				e.printStackTrace();
				appendToTextView(e.toString());
			}			
		}
	};
	
	// TCP/IP SERVER AND CLIENT THREAD
	public class ServerThread implements Runnable {
	    private ServerSocketChannel ss;
	    private SocketChannel sc;
	    private boolean error = false;
	    private boolean connected = true;
	    private long startTime;
		public void run() {
			Log.i("MAVCell", "!!!!!!!!***********FST!!!************!!!!!!!!!!!!!!");
			try {
				Selector selector = Selector.open();
				if (isServer) {
			        ss = ServerSocketChannel.open();
					ss.configureBlocking(false);
			        ss.socket().bind(new InetSocketAddress(port));
			        ss.register(selector, SelectionKey.OP_ACCEPT);
			        appendToTextView("Server Ready.\r\n");
				}
				else {
					appendToTextView("Trying to Connect.\r\n");
					SocketAddress sa = new InetSocketAddress(InetAddress.getByName(serverIPAddr), port);
					sc = SocketChannel.open();
					sc.configureBlocking(false);
					sc.connect(sa);
					sc.register(selector, SelectionKey.OP_CONNECT);
				}
				startTime = System.nanoTime();
		        while (!mStop && !error) {
		        	selector.select();
		        	Iterator<SelectionKey> it = selector.selectedKeys().iterator();
		        	while (it.hasNext() && !error) {
		        		SelectionKey selKey = (SelectionKey)it.next();
		        		it.remove();
		        		// CLIENT CONNECT TIMEOUT HANDLING
		        		if (!isServer && !connected && (System.nanoTime()-startTime)/1000000 > 500) {
		        			appendToTextView("Connection Timed Out.\r\n");
		        			error = true;
		        			break;		        			
		        		}
		        		if (selKey.isConnectable() && !isServer) {
		        			SocketChannel s = (SocketChannel)selKey.channel();
		        			s.configureBlocking(false);
		        			s.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		        			s.finishConnect();
		        			connected = true;
		        			appendToTextView("Connection Successful.\r\n");
		        			continue;
		        		}
		        		if (selKey.isAcceptable() && isServer) {
		        			ServerSocketChannel ssChannel = (ServerSocketChannel)selKey.channel();
		        			SocketChannel s = ssChannel.accept();
		        			s.configureBlocking(false);
		        			s.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		        			appendToTextView("Client Connected.\r\n");
		        			continue;
		        		}
		        		if (selKey.isReadable()) {
		        			SocketChannel s = (SocketChannel) selKey.channel();
		        			int BUFFER_SIZE = 4096;
		        			ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
		        			byte[] byteArray = buffer.array();
		        			try {
		        				int len = s.read(buffer);
		        				if (len > 0) {
				        			String decoded = Base64.encodeToString(byteArray, 0, len, Base64.DEFAULT);
				        			inQueue.add(decoded);
				        			if (debugging)
				        				appendToTextView(outQueue.size() + ":"+ len + "\r\n");
		        				}
		        				else if (len == -1) {
		        					appendToTextView("Connection lost.\r\n");
		        					error = true;
		        					if (selector != null && selector.isOpen())
		        						selector.close();
		        					if (s != null && s.isOpen())
		        						s.close();
		        					break;
		        				}
							}
		        			catch (Exception e) {
		        				e.printStackTrace();
		        				continue;
		        			}
		        			buffer.flip();
		        			continue;
		        		}
		        		if (selKey.isWritable() && outQueue.size() > 0)
		        		{
		        			SocketChannel s = (SocketChannel) selKey.channel();
		        			for (int i = 0; i < outQueue.size(); i++) {
			        			String temp = outQueue.remove();
			        			byte[] byteArray = Base64.decode(temp, Base64.DEFAULT);
			        			ByteBuffer buffer = ByteBuffer.allocate(byteArray.length + 8);
			        			buffer.put(byteArray);
			        			buffer.flip();
			        			s.write(buffer);
		        			}
		        		}
						Thread.sleep(10);
		        	}
		        }
		        if (ss != null && sc.isOpen())
		        	sc.close();
		        if (ss != null && ss.isOpen())
		        	ss.close();
		        if (selector != null && selector.isOpen())
		        	selector.close();
			} catch (Exception e) {
				e.printStackTrace();
				appendToTextView(e.toString());
			}
		}
	};
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("MAVCell", "!!!!!!!!***********DESTROYED!!!************!!!!!!!!!!!!!!");
    	wl.release();
    	wifil.release();
        if (mSerial.isConnected()) {
        	mSerial.end();
        }
     	mStop = true;
     	if (mont != null && mont.isAlive())
     		mont.interrupt();
     	if (spt != null && spt.isAlive())
     		spt.interrupt();
     	if (fst != null && fst.isAlive())
     		fst.interrupt();
     	mont = null;
     	spt = null;
     	fst = null;
     	finish();
    }
    
    public void onBeginClick(View view) { 
    	Log.i("MAVCell", "!!!!!!!!***********BEGIN!!!************!!!!!!!!!!!!!!");
    	if (port < 0 || port > 65535) {
    		Toast.makeText(this, "Invalid Port.", Toast.LENGTH_SHORT).show();
    		return;
    	}
    	if (baud != 9600 && baud != 38400 && baud != 57600 && baud != 115200) {
    		Toast.makeText(this, "Invalid Baud.", Toast.LENGTH_SHORT).show();
    		return;
    	}
    	else {
    		switch (baud) {
    		case 4800:
    			mSerialBaud = FTDriver.BAUD4800;
    			break;
    		case 9600:
    			mSerialBaud = FTDriver.BAUD9600;
    			break;
    		case 38400:
    			mSerialBaud = FTDriver.BAUD38400;
    			break;
    		case 57600:
    			mSerialBaud = FTDriver.BAUD57600;
    			break;
    		case 115200:
    			mSerialBaud = FTDriver.BAUD115200;
    			break;
    		}
    	}
        if(mSerial.begin(mSerialBaud)) {
        	mStop = false;
        	mont = new Thread(new MonitorThread());
        	fst = new Thread(new ServerThread());
        	spt = new Thread(new SerialPortThread());
        	connected = true;
            btnBegin.setEnabled(false);
            btnRead.setEnabled(true);
            btnEnd.setEnabled(true); 
            baudRate.setEnabled(false);
            portNum.setEnabled(false);
            if (isServer)
            	serverIP.setEnabled(false);
            else
            	serverIP.setEnabled(true);
            serverMode.setEnabled(false);
            Toast.makeText(this, "COM Port Opened.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Cannot Open COM.", Toast.LENGTH_SHORT).show();
        }
    }
    
    public void onReadClick(View view) {
    	Log.i("MAVCell", "!!!!!!!!***********READ!!!************!!!!!!!!!!!!!!");
    	if (connected)
    	{
    		btnRead.setEnabled(false);
    		appendToTextView("Port Opened.\r\n");
    		mont.start();
    	}
    }
    
    public void onEndClick(View view) {
    	Log.i("MAVCell", "!!!!!!!!***********END!!!************!!!!!!!!!!!!!!");
    	mStop = true;
    	fst = null;
    	spt = null;
    	if (mSerial.isConnected()) {
    		mSerial.end();
    	}
        connected = false;
        btnBegin.setEnabled(true);
        btnRead.setEnabled(false);
        btnEnd.setEnabled(false);
        baudRate.setEnabled(true);
        portNum.setEnabled(true);
        if (!isServer)
        	serverIP.setEnabled(true);
        Toast.makeText(this, "Disconnected.", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    protected void onStop() {
    	Log.i("MAVCell", "!!!!!!!!***********STOP!!!************!!!!!!!!!!!!!!");
        super.onStop();
    }
}
