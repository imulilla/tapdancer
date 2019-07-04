package co.kica.tapdancer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.util.Log;

import co.kica.fileutils.SmartFileInputStream;
import co.kica.tap.AtariTape;
import co.kica.tap.C64Program;
import co.kica.tap.C64Tape;
import co.kica.tap.GenericTape;
import co.kica.tap.IntermediateBlockRepresentation;
import co.kica.tap.MSXTape;
import co.kica.tap.TZXTape;
import co.kica.tap.UEFTape;
import co.kica.tap.ZXTAP;

public class RenderRunnable implements Runnable {

	private String tapfile;
	private RenderActivity mActivity;
	private int result = Activity.RESULT_CANCELED;
	public boolean signal = true;
	private int index;

	public RenderRunnable( RenderActivity mActivity, String tapfile, int idx ) {
		this.tapfile = tapfile;
		this.mActivity = mActivity;
		this.index = idx;
	}
	
	public void cancel() {
		this.signal = false;
	}
	
	@Override
	public void run() {
		// here goes the code that executes the runnable
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this.mActivity);
		
		boolean useLowSampleRate = sharedPrefs.getBoolean("prefUseLowSampleRate", false);
		int sr = 44100;
		if (useLowSampleRate) {
			sr = 22050;
		}
		
		String tapfile = this.tapfile;
		//String fn = (new File(tapfile)).getName().replace(".tap", ".wav").replace(".TAP", ".wav").replace(".CAS", ".wav").replace(".cas", ".wav");
		
		// test if output dir exists
		String outputdir = Environment.getExternalStorageDirectory()+"/TapDancer";
		
		File od = new File(outputdir);
		if (!od.exists()) {
			od.mkdirs();
			// write no media to the dir
			File nm = new File(outputdir+"/.nomedia");
			try {
				nm.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				Log.w(getClass().getName(), "Exceptions generating nomedia tag", e);
			}
		}
		
		//String outputpath = (new File( Environment.getExternalStorageDirectory(), fn )).getAbsolutePath();
		String md5 = "0000000000000000000000000000000";
				
		try {
			md5 = getMD5DigestForFile(tapfile);
		} catch (Exception e2) {
			// TODO Auto-generated catch block
			Log.w(getClass().getName(), "Exceptions generating file checksum ", e2);
		}
		
		if (index > 0)
			md5 = md5 + "."+Integer.toHexString(index);
		
		String outputpath = outputdir+"/"+md5+".manifest";
		String baseName = md5;
		String basePath = outputdir;
		
		File f = new File(outputpath);
		
		// make PRG / T64 re-render if the option has changed
		if (f.exists()) {
			String type = sharedPrefs.getString("prefPRGLoaderType", "1");
			int o_type = Integer.parseInt(type);
			
			if (tapfile.toLowerCase().contains(".prg") || tapfile.toLowerCase().contains(".p00") ||
					tapfile.toLowerCase().contains(".t64")) {
				// check if it was with the same loader
				IntermediateBlockRepresentation ibr = new IntermediateBlockRepresentation(basePath, baseName);
				if (ibr.getLoaderType() != o_type) {
					f.delete();
				}
				ibr = null;
				System.gc();
			} else if (tapfile.toLowerCase().contains(".tzx") || tapfile.toLowerCase().contains(".tsx")||tapfile.toLowerCase().contains(".tap")) {
				IntermediateBlockRepresentation ibr = new IntermediateBlockRepresentation(basePath, baseName);
				if (ibr.getRenderedSampleRate() != sr) {
					f.delete();
				}
				ibr = null;
				System.gc();
			}
		}
		f.delete();//borrrraaa
		if (!f.exists()) {
		
			Log.i(getClass().getName(), "Rendering audio to "+outputpath);
			
			try {
				C64Tape tap = new C64Tape();
				tap.Load(tapfile);
				if (tap.isValid()) {
					Thread t = new Thread(new RenderPercentPublisher(tap, this));
					t.start();
					tap.writeAudioStreamData(basePath, baseName);
					result = Activity.RESULT_OK;
				} else {
					tap = null;
					MSXTape msx = new MSXTape();
					msx.Load(tapfile);
					if (msx.isValid()) {
						Thread t = new Thread(new RenderPercentPublisher(msx, this));
						t.start();
						msx.writeAudioStreamData(basePath, baseName);
						result = Activity.RESULT_OK;
					} else {
						msx = null;
						
						UEFTape uef = new UEFTape();
						uef.Load(tapfile);
						if (uef.isValid()) {
							Thread t = new Thread(new RenderPercentPublisher(uef, this));
							t.start();
							uef.writeAudioStreamData(basePath, baseName);
							result = Activity.RESULT_OK;
						} else {
							uef = null;
							
							TZXTape tzx = new TZXTape(sr);
							tzx.Load(tapfile);
							if (tzx.isValid()) {
								Thread t = new Thread(new RenderPercentPublisher(tzx, this));
								t.start();
								tzx.writeAudioStreamData(basePath, baseName);
								result = Activity.RESULT_OK;
							} else {
								AtariTape fuji = new AtariTape();
								fuji.Load(tapfile);
								if (fuji.isValid()) {
									Thread t = new Thread(new RenderPercentPublisher(fuji, this));
									t.start();
									fuji.writeAudioStreamData(basePath, baseName);
									result = Activity.RESULT_OK;
								} else {
									fuji = null;
									if (tapfile.toLowerCase().contains(".prg") ||
											tapfile.toLowerCase().contains(".t64") ||
											tapfile.toLowerCase().contains(".p00")) {
										
										String type = sharedPrefs.getString("prefPRGLoaderType", "1");
										int o_type = Integer.parseInt(type);
										C64Program prg = new C64Program();
										prg.setIdx(index);
										prg.setLoadModel(o_type);
										prg.Load(tapfile);
										Thread t = new Thread(new RenderPercentPublisher(prg, this));
										t.start();
										prg.writeAudioStreamData(basePath, baseName);
										result = Activity.RESULT_OK;
									} else {
										
										ZXTAP zxt = new ZXTAP(sr);
										zxt.Load(tapfile);
										
										if (zxt.isValid()) {
											Thread t = new Thread(new RenderPercentPublisher(zxt, this));
											t.start();
											zxt.writeAudioStreamData(basePath, baseName);
											result = Activity.RESULT_OK;
										} else {
											result = Activity.RESULT_CANCELED;
										}
									}
								}
							}
						}
					}
				}
			} catch (Exception e) {
				Log.w(getClass().getName(), "Exceptions rendering audio", e);
			}
		
		} else {
			result = Activity.RESULT_OK;
		}
		
		if (signal) {
			Messenger messenger = mActivity.getMessenger();
			Message msg = Message.obtain();
			msg.arg1 = result;
			msg.obj = basePath+":"+baseName;
			try {
				messenger.send(msg);
			} catch (android.os.RemoteException e1) {
				Log.w(getClass().getName(), "Exception sending message", e1);
			}
		}

	}
	
	private  byte[] createChecksum(String filename) throws Exception {
	       InputStream fis =  new SmartFileInputStream(filename);

	       byte[] buffer = new byte[1024];
	       MessageDigest complete = MessageDigest.getInstance("MD5");
	       int numRead;

	       do {
	           numRead = fis.read(buffer);
	           if (numRead > 0) {
	               complete.update(buffer, 0, numRead);
	           }
	       } while (numRead != -1);

	       fis.close();
	       return complete.digest();
	}
	
	private String getMD5DigestForFile( String filename ) throws Exception {
		       byte[] b = createChecksum(filename);
		       String result = "";

		       for (int i=0; i < b.length; i++) {
		           result += Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
		       }
		       return result;
	}
	
	public class RenderPercentPublisher implements Runnable {
		
		private GenericTape tape;
		private RenderRunnable rs;

		public RenderPercentPublisher( GenericTape tape, RenderRunnable rs ) {
			this.tape = tape;
			this.rs = rs;
		}

		@Override
		public void run() {
			
			while (tape.getRenderPercent() < 1) {
				// publish it and sleep
				try {
					rs.sendPercentMessage( Math.round(100*tape.getRenderPercent()) );
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		}
		
	}
	
	public void sendPercentMessage(int round) {
			Messenger messenger = mActivity.getMessenger();
			Message msg = Message.obtain();
			msg.arg1 = 364;    // simple message id so we know what to do with it...
			msg.arg2 = round;
			try {
				messenger.send(msg);
			} catch (android.os.RemoteException e1) {
				Log.w(getClass().getName(), "Exception sending message", e1);
			}
	}

}
