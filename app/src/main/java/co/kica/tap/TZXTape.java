package co.kica.tap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import co.kica.tap.TZXTape.TZXChunk;

public class TZXTape extends GenericTape {
	
	public static final double ZXTick = 1.0/3500000.0;
	public int minorVersion = 1;
	public int majorVersion = 21;
	private TZXChunk lastChunk;
	public static final double PULSE_AMPLITUDE = -0.99;
	public static final double PULSE_MIDDLE = 0.99;
	public static final double PULSE_REST = 0.99;
	public static final double PAL_CLK = 3500000;
	public static final double MU = 1000000;
	
	public int[] blockCounts = new int[256];
	private int coreCounter;
	private int savePosition;
	
	public class TZXChunk {
		public int id = 0;
		public int pauseAfter = 1000;
		public int dataSize = 0;
		public byte[] chunkData;
		public int pilotPulseLength = 2168;
		public int syncFirstPulseLength = 667;
		public int syncSecondPulseLength = 735;
		public int zeroBitPulseLength = 855;
		public int oneBitPulseLength = 1710;
		public int pilotPulseCount = 8064;
		public int usedBitsLastByte = 8;
		public int dataPulseCount = 0;
		public int ticksPerBit;
		public int blockLength;
		public int sampleRate;
		public int compressionType;
		public int CSWPulseCount;
		public int relativeJump;
		public int numberRepetitions;
		public String description;
	}
	
	public TZXTape( int sampleRate ) {
		super();
		this.setTargetSampleRate( sampleRate );
	}

	@Override
	public boolean parseHeader(InputStream f) {
		byte[] buff = new byte[headerSize()];
		
		for (int i=0; i<blockCounts.length; i++) {
			blockCounts[i] = 0;
		}
		
		try {
			//f.reset();
			int len = f.read(buff);
			
			if (len == headerSize()) {
				// reset and store into header
				Header.reset();
				Header.write(buff);
				byte[] magic = new byte[] {'Z', 'X', 'T', 'a', 'p', 'e', '!'};
				if (Arrays.equals(getMAGIC(),magic)) {
					System.out.println("*** File is a valid TZX by the looks of it.");
					setValid(true);
					setMajorVersion(buff[0x08] & 0xff);
					setMinorVersion(buff[0x09] & 0xff);
					System.out.println("*** Header says version is "+getMajorVersion()+"."+getMinorVersion());
					setStatus(tapeStatusOk);
					return true;
				} else {
					System.out.println("xxx File has unrecognized magic: "+getMAGIC());
					setStatus(tapeStatusHeaderInvalid);
					return false;
				}
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	public boolean hasData() {
		return (dataPos < Data.size());
	}
	
	public int getDataByte(byte[] data) {
		if (!hasData()) {
			return 0;
		}
		return data[dataPos++] & 0xff;
	}
	
	public int getSizeFromChunk(byte[] data) {
		ByteBuffer b = ByteBuffer.allocate(4);
		b.order(ByteOrder.LITTLE_ENDIAN);
		b.mark();
		b.put( Arrays.copyOfRange(data, dataPos, dataPos+3) );
		b.reset();
		dataPos += 4;
		return b.getInt();
	}
	
	public float getFloatFromChunk(byte[] data) {
		ByteBuffer b = ByteBuffer.allocate(4);
		b.order(ByteOrder.LITTLE_ENDIAN);
		b.mark();
		b.put( Arrays.copyOfRange(data, 0, 3) );
		b.reset();
		return b.getFloat();
	}
	
	public int getDataWord(byte[] data) {
		return getDataByte(data) + 256*getDataByte(data);
	}
	
	public int getDataTriplet(byte[] data) {
		return getDataByte(data) + 256*getDataByte(data) + 65536*getDataByte(data);
	}
	
	public int getDataDWORD(byte[] data) {
		return getDataByte(data) + 256*getDataByte(data) + 65536*getDataByte(data) + 16777216*getDataByte(data);
	}
	
	public TZXChunk getNextChunk(byte[] data) {
		if (!hasData()) {
			return null;
		}
		
		TZXChunk chunk = new TZXChunk();
		chunk.id = getDataByte(data);
		int size = 0;
		
		/* work out based on block how much data we need to snaffle */
		boolean ok = true;
		
		switch (chunk.id) {
		case 0x10:		chunk.description = "Standard speed data block";
						chunk.pauseAfter = getDataWord(data);
						size = getDataWord(data);
						break;
		case 0x11:		chunk.description = "Turbo speed data block";
						chunk.pilotPulseLength = getDataWord(data);
						chunk.syncFirstPulseLength = getDataWord(data);
						chunk.syncSecondPulseLength = getDataWord(data);
						chunk.zeroBitPulseLength = getDataWord(data);
						chunk.oneBitPulseLength = getDataWord(data);
						chunk.pilotPulseCount = getDataWord(data);
						chunk.usedBitsLastByte = getDataByte(data);
						chunk.pauseAfter = getDataWord(data);
						size = getDataTriplet(data);
						break;
		case 0x12:		chunk.description = "Pure tone block";
						chunk.pilotPulseLength = getDataWord(data);
						chunk.pilotPulseCount = getDataWord(data);
						break;
		case 0x13:		chunk.description = "Pulse sequence";
						chunk.dataPulseCount = getDataByte(data);
						size = chunk.dataPulseCount * 2;
						break;
		case 0x14:		chunk.description = "Pure data block";
						chunk.zeroBitPulseLength = getDataWord(data);
						chunk.oneBitPulseLength = getDataWord(data);
						chunk.usedBitsLastByte = getDataByte(data);
						chunk.pauseAfter = getDataWord(data);
						size = getDataTriplet(data);
						break;
		case 0x15:		chunk.description = "Direct recording";
						chunk.ticksPerBit = getDataWord(data);
						chunk.pauseAfter = getDataWord(data);
						chunk.usedBitsLastByte = getDataByte(data);
						size = getDataTriplet(data);
						break;
		case 0x18:		chunk.description = "CSW recording";
						size = getDataDWORD(data) - 10;
						chunk.pauseAfter = getDataWord(data);
						chunk.sampleRate = getDataTriplet(data);
						chunk.compressionType = getDataByte(data);
						chunk.CSWPulseCount = getDataWord(data);
						break;
		case 0x19:		chunk.description = "Generalized torture block";
						/* TODO: YOU BASTARDS!!!!!!!!! */
						size = getDataDWORD(data);
						break;
		case 0x20:		chunk.description = "Silence (stop tape)";
						chunk.pauseAfter = getDataWord(data);
						break;
		case 0x21:		chunk.description = "Group start";
						size = getDataByte(data);
						break;
		case 0x22:		chunk.description = "Group end";
						break;
		case 0x23:		chunk.description = "Jump to block";
						chunk.relativeJump = getDataWord(data);
						break;
		case 0x24:		chunk.description = "Loop start";
						chunk.numberRepetitions = getDataWord(data);
						break;
		case 0x25:		chunk.description = "Loop end";
						break;
		case 0x26:		chunk.description = "Call sequence";
						size = getDataWord(data)*2;
		case 0x27:		chunk.description = "Return from sequence";
						break;
		case 0x28:		chunk.description = "Select block";
						size = getDataWord(data);
						break;
		case 0x2a:		chunk.description = "Stop tape if 48K";
						getDataDWORD(data);
						break;
		case 0x2b:		chunk.description = "Set signal level";
						size = getDataDWORD(data);
						break;
		case 0x30:		chunk.description = "Text description";
						size = getDataByte(data);
						break;
		case 0x31:		chunk.description = "Message block";
						chunk.pauseAfter = getDataByte(data);;
						size = getDataByte(data);
						break;
		case 0x32:		chunk.description = "Archive information";
						size = getDataWord(data);
						break;
		case 0x33:		chunk.description = "Hardware info";
						size = getDataByte(data)*3;
						break;
		case 0x35:		chunk.description = "Custom info block";
						this.dataPos += 0x10;
						size = getDataDWORD(data);
						break;
		case 0x4b:		chunk.description = "Kansas city standard block";
						size = getDataDWORD(data)/*UA_L32   blockLen;          //Block length without these four bytes*/
						chunk.pauseAfter = getDataWord(data);/*UA_L16   pausems; //Pause after this block in milliseconds*/
						chunk.pilotPulseLength = getDataWord(data);/*UA_L16   pilot;       //Duration of a PILOT pulse in T-states {same as ONE pulse}*/
						chunk.pilotPulseCount = getDataWord(data);/*UA_L16   pulses;            //Number of pulses in the PILOT tone*/
						chunk.zeroBitPulseLength = getDataWord(data);/*UA_L16   bit0len;           //Duration of a ZERO pulse in T-states {=2*pilot}*/
						chunk.oneBitPulseLength = getDataWord(data);/*UA_L16   bit1len;           //Duration of a ONE pulse in T-states {=pilot}*/
						/*uint8_t  bitcfg = MSX_BITCFG; 0x24*/
						/*uint8_t  bytecfg = MSX_BYTECFG; 0x54*/
						break;
		case 0x5d:		chunk.description = "Glue block??!!";
						size = 9;
						break;
		default:		chunk.description = "UNKNOWN BLOCK TYPE "+Integer.toHexString(chunk.id);
						ok = false;
						break;
		}
		
		if (!ok) {
			System.out.println("["+this.FileName+"] Unrecognized block: 0x0"+Integer.toHexString(chunk.id));
		}
		
		chunk.chunkData = new byte[size];
		if (ok)
			blockCounts[chunk.id] = blockCounts[chunk.id] + 1;
		
		for (int i=0; i<size; i++) {
			chunk.chunkData[i] = (byte)getDataByte(data);
		}
		
		if (chunk.id == 0x24) {
			// save current position..
			this.coreCounter = chunk.numberRepetitions;
			this.savePosition = this.dataPos;
		}
		
		if (chunk.id == 0x25) {
			this.coreCounter--;
			if (this.coreCounter > 0) {
				this.dataPos = this.savePosition;
			}
		}
		
		return chunk;
	}

	@Override
	public byte[] buildHeader() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int headerSize() {
		return 10;
	}

	@Override
	public int minPadding() {
		return 0;
	}

	@Override
	public byte[] getMAGIC() {
		byte[] magic = Arrays.copyOfRange(Header.toByteArray(), 0, 7);
		return magic;
	}

	@Override
	public void writeAudioStreamData(String path, String base) {
		IntermediateBlockRepresentation w = new IntermediateBlockRepresentation(path, base);
		w.setSampleRate(this.getTargetSampleRate());
		
		w.setSystem(this.getTapeType());
		
		//Data.reset();
		byte[] raw = Data.toByteArray();
		
		int bytesread = 0;
		double duration = 0;
		double cnv = 1.0;
		
		while (hasData()) {
			TZXChunk chunk = getNextChunk(raw);
			
			//System.out.println("Got a chunk with ID "+Integer.toHexString(chunk.id)+" ("+chunk.description+") with size "+chunk.chunkData.length+" bytes.");
			
			try {
				handleChunk(chunk, w);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			// store last block for &101
			lastChunk = chunk;
		}
		
		// do cue
		w.done();

	}
	
	public boolean hasBlock18() {
		
		//Data.reset();
		byte[] raw = Data.toByteArray();
		
		while (hasData()) {
			TZXChunk chunk = getNextChunk(raw);
			
			//System.out.println("Got a chunk with ID "+Integer.toHexString(chunk.id)+" ("+chunk.description+") with size "+chunk.chunkData.length+" bytes.");
			if (chunk.id == 0x18) {
				return true;
			}
			
			// store last block for &101
			lastChunk = chunk;
		}
		
		return false;

	}

	private void handleChunk(TZXChunk chunk, IntermediateBlockRepresentation w) {
		switch (chunk.id) {
		case 0x10: 	handleChunk0x10(w, chunk); break;
		case 0x11:	handleChunk0x11(w, chunk); break;
		case 0x12:	handleChunk0x12(w, chunk); break;
		case 0x13:	handleChunk0x13(w, chunk); break;
		case 0x14:	handleChunk0x14(w, chunk); break;
		case 0x15:	handleChunk0x15(w, chunk); break;
		case 0x20: 	handleChunk0x20(w, chunk); break;
		}
	}

	@Override
	public boolean isHeaderData() {
		return false;
	}

	@Override
	public String getTapeType() {
		// TODO Auto-generated method stub
		return "TZX";
	}

	@Override
	public float getRenderPercent() {
		// TODO Auto-generated method stub
		return (float)this.dataPos/(float)this.Data.size();
	}

	public int getMinorVersion() {
		return minorVersion;
	}

	public void setMinorVersion(int minorVersion) {
		this.minorVersion = minorVersion;
	}

	public int getMajorVersion() {
		return majorVersion;
	}

	public void setMajorVersion(int majorVersion) {
		this.majorVersion = majorVersion;
	}
	
	public void writePilotTone( IntermediateBlockRepresentation w, int pulseLength, int numPulses ) {
		double duration = ((double)pulseLength * 1000000.0) / 3500000.0;	
		for (int i=0; i<numPulses; i++) {
			w.addPulse(duration, PULSE_AMPLITUDE);
		}
	}
	
	private void writePulse(IntermediateBlockRepresentation w, int len) {
		double duration = ((double)len * 1000000.0) / 3500000.0;	
		//float duration = ((float)len * 1000000f) / 3500000f;
		w.addPulse(duration, PULSE_AMPLITUDE);
	}
	
	private void writeDirectRecordingBlock(IntermediateBlockRepresentation w,
			TZXChunk chunk) {
		
		int sampleCount = chunk.ticksPerBit / 79;
		
		for (int i=0; i<chunk.chunkData.length; i++) {
			
			int bc = 8;
			if (i == chunk.chunkData.length-1) {
				bc = chunk.usedBitsLastByte;
			}
			
			int b = chunk.chunkData[i] & 0xff;
			
			while (bc > 0) {
				int bit = (b & 0x80); 
				if ((bit & 0x80) == 0x80) {
					// one - ear = 1
					w.writeSamples( (long)sampleCount, (byte)1, PULSE_AMPLITUDE );
				} else {
					// zero - ear = 0
					w.writeSamples( (long)sampleCount, (byte)0, PULSE_AMPLITUDE );
				}
				b = b << 1;
				bc--;
			}
			
		}
		
		//System.out.println();
	}
	
	private void writeDataBlock(IntermediateBlockRepresentation w,
			TZXChunk chunk) {
		
		for (int i=0; i<chunk.chunkData.length; i++) {
			
			int bc = 8;
			int fb = 0;
			if (i == chunk.chunkData.length-1) {
				bc = chunk.usedBitsLastByte;
				fb = 8 - bc;
			}
			
			int b = chunk.chunkData[i] & 0xff;
			
			//System.out.print(Integer.toHexString(b)+" ");
			
			//if ((i % 16) == 0) 
				//System.out.println("\n");
			
			while (bc > 0) {
				int bit = (b & 0x80); 
				if ((bit & 0x80) == 0x80) {
					// one
					//System.out.print("1");
					writePulse( w, chunk.oneBitPulseLength );
					writePulse( w, chunk.oneBitPulseLength );
				} else {
					// zero
					//System.out.print("0");
					writePulse( w, chunk.zeroBitPulseLength );
					writePulse( w, chunk.zeroBitPulseLength );
				}
				b = b << 1;
				bc--;
			}
		}
		
		//System.out.println();
	}
	
	/*
	Block 0x10: 2209 times.
	Block 0x11: 2851 times.
	Block 0x12: 182 times.
	Block 0x13: 160 times.
	Block 0x14: 12 times.
	Block 0x20: 44 times.
	 */
	
	/* silencio! */
	public void handleChunk0x20( IntermediateBlockRepresentation w, TZXChunk chunk ) {
		w.addPause( 1000*chunk.pauseAfter, PULSE_AMPLITUDE);
	}
	
	public void handleChunk0x15( IntermediateBlockRepresentation w, TZXChunk chunk ) {
		writeDirectRecordingBlock(w, chunk);
		w.addPause( 1000*chunk.pauseAfter, PULSE_AMPLITUDE);
	}
	
	/* standard data */
	public void handleChunk0x10( IntermediateBlockRepresentation w, TZXChunk chunk ) {
		// write pilot
		int flag = chunk.chunkData[0]&0xff;
		//System.out.println("Flag: "+Integer.toHexString(flag));
		
		if (flag >= 128) 
			chunk.pilotPulseCount = 3220;
		// PILOT TONE
		writePilotTone( w, chunk.pilotPulseLength, chunk.pilotPulseCount );
		// SYNC PULSE 1ST
		writePulse( w, chunk.syncFirstPulseLength );
		// SYNC PULSE 2ND
		writePulse( w, chunk.syncSecondPulseLength );
		// data
		writeDataBlock( w, chunk );
		// pause
		w.addPause( 1000*chunk.pauseAfter, PULSE_AMPLITUDE);
	}
	
	/* turbo data */
	public void handleChunk0x11( IntermediateBlockRepresentation w, TZXChunk chunk ) {
		// PILOT TONE
		writePilotTone( w, chunk.pilotPulseLength, chunk.pilotPulseCount );
		// SYNC PULSE 1ST
		writePulse( w, chunk.syncFirstPulseLength );
		// SYNC PULSE 2ND
		writePulse( w, chunk.syncSecondPulseLength );
		// data
		writeDataBlock( w, chunk );
		// pause
		w.addPause( 1000*chunk.pauseAfter, PULSE_AMPLITUDE);
	}

	/* pure tone */
	public void handleChunk0x12( IntermediateBlockRepresentation w, TZXChunk chunk ) {
		//System.out.println("INFO: "+chunk.pilotPulseCount+" pulses of "+chunk.pilotPulseLength+" T-States...");
		
		writePilotTone( w, chunk.pilotPulseLength, chunk.pilotPulseCount );
	}
	
	/* pulse sequence */
	public void handleChunk0x13( IntermediateBlockRepresentation w, TZXChunk chunk ) {
		int idx = 0;
		while (idx < chunk.chunkData.length) {
			int val = (chunk.chunkData[idx++] & 0xff) + 256*(chunk.chunkData[idx++] & 0xff);
			//System.out.println("INFO: PULSE of "+val+" T-states.");
			writePulse(w, val);
			//idx += 2;
		}
	}
	
	/* pure data block */
	public void handleChunk0x14( IntermediateBlockRepresentation w, TZXChunk chunk ) {	
		writeDataBlock( w, chunk );
		// pause
		w.addPause( 1000*chunk.pauseAfter, PULSE_AMPLITUDE);
	}
	public void handleChunk0x4b( IntermediateBlockRepresentation w, TZXChunk chunk ) {

		pulsePilot4B = ULTRA_SPEED ? TSTATES_MSX_PULSE : b->pilot;
		pulseOne4B   = ULTRA_SPEED ? TSTATES_MSX_PULSE : b->bit1len;
		pulseZero4B  = ULTRA_SPEED ? TSTATES_MSX_PULSE*2 : b->bit0len;
		numZeroPulses4B = (b->bitcfg & 0b11110000) >> 4;
		numOnePulses4B = (b->bitcfg & 0b00001111);
		if (numZeroPulses4B==0) numZeroPulses4B=16;
		if (numOnePulses4B==0) numOnePulses4B=16;
		byteStartBits4B  = (b->bytecfg & 0b11000000) >> 6;
		byteStartValue4B = (b->bytecfg & 0b00100000) >> 5;
		byteStopBits4B   = (b->bytecfg & 0b00011000) >> 3;
		byteStopValue4B  = (b->bytecfg & 0b00000100) >> 2;
		msb4B = (b->bytecfg & 0b00000001);

		writeHeader4B(ULTRA_SPEED ? 5000 : b->pulses);

		uint32_t size = b->blockLen - 12;
		byte *data = b->data;
		while (size--) {
			writeByte4B(*data++);
			// write a MSX #4B byte
			void TsxImage::writeByte4B(byte b)
			{
				uint8_t t;
				// start bits
				for (t=0; t<byteStartBits4B; t++) {
					if (byteStartValue4B) write1(); else write0();
				}
				// eight data bits
				for (auto i : xrange(8)) {
					if (msb4B) {
						if (b & (1 << (7-i))) {
							write1();
						} else {
							write0();
						}
					} else {
						if (b & (1 << i)) {
							write1();
						} else {
							write0();
						}
					}
				}
				// stop bits
				for (t=0; t<byteStopBits4B; t++) {
					if (byteStopValue4B) write1(); else write0();
				}
			}
		}
		/*writeSilence(ULTRA_SPEED ? 100 : b->pausems)*/
		w.addPause( 1000*chunk.pauseAfter, PULSE_AMPLITUDE);
		return b->blockLen + 5
	}
}

