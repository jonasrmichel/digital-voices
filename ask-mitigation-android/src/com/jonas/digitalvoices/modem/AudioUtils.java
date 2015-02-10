package com.jonas.digitalvoices.modem;

/**
 * Copyright 2002 by the authors. All rights reserved.
 *
 * Author: Cristina V Lopes
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import net.fec.openrq.ArrayDataEncoder;
import net.fec.openrq.EncodingPacket;
import net.fec.openrq.OpenRQ;
import net.fec.openrq.encoder.SourceBlockEncoder;
import net.fec.openrq.parameters.FECParameters;

/**
 * 
 * @author CVL
 */

public class AudioUtils {

	/*
	 * //the default format for reading and writing audio information public
	 * static AudioFormat kDefaultFormat = new AudioFormat((float)
	 * Encoder.kSamplingFrequency, (int) 8, (int) 1, true, false);
	 */

	/*
	 * public static void decodeWavFile(File inputFile, OutputStream out) throws
	 * UnsupportedAudioFileException, IOException { StreamDecoder sDecoder = new
	 * StreamDecoder(out); AudioBuffer aBuffer = sDecoder.getAudioBuffer();
	 * 
	 * AudioInputStream audioInputStream =
	 * AudioSystem.getAudioInputStream(kDefaultFormat,
	 * AudioSystem.getAudioInputStream(inputFile)); int bytesPerFrame =
	 * audioInputStream.getFormat().getFrameSize(); // Set an arbitrary buffer
	 * size of 1024 frames. int numBytes = 1024 * bytesPerFrame; byte[]
	 * audioBytes = new byte[numBytes]; int numBytesRead = 0; // Try to read
	 * numBytes bytes from the file and write it to the buffer
	 * ByteArrayOutputStream baos = new ByteArrayOutputStream(); while
	 * ((numBytesRead = audioInputStream.read(audioBytes)) != -1) {
	 * 
	 * aBuffer.write(audioBytes, 0, numBytesRead); } }
	 */

	/*
	 * public static void writeWav(File file, byte[] data, AudioFormat format)
	 * throws IllegalArgumentException, IOException { ByteArrayInputStream bais
	 * = new ByteArrayInputStream(data); AudioInputStream ais = new
	 * AudioInputStream(bais, format, data.length); AudioSystem.write(ais,
	 * AudioFileFormat.Type.WAVE, file); }
	 */

	/*
	 * public static void displayMixerInfo(){ Mixer.Info[] mInfos =
	 * AudioSystem.getMixerInfo(); if(mInfos == null){
	 * System.out.println("No Mixers found"); return; }
	 * 
	 * for(int i=0; i < mInfos.length; i++){ System.out.println("Mixer Info: " +
	 * mInfos[i]); Mixer mixer = AudioSystem.getMixer(mInfos[i]); Line.Info[]
	 * lines = mixer.getSourceLineInfo(); for(int j = 0; j < lines.length; j++){
	 * System.out.println("\tSource: " + lines[j]); } lines =
	 * mixer.getTargetLineInfo(); for(int j = 0; j < lines.length; j++){
	 * System.out.println("\tTarget: " + lines[j]); } } }
	 */

	/*
	 * public static void displayAudioFileTypes(){ AudioFileFormat.Type[] types
	 * = AudioSystem.getAudioFileTypes(); for(int i=0; i < types.length; i++){
	 * System.out.println("Audio File Type:" + types[i].toString()); } }
	 */

	/*
	 * //This never returns, which is kind of lame. // NOT USED!! - replaced by
	 * MicrophoneListener.run() public static void
	 * listenToMicrophone(AudioBuffer buff){ try { int buffSize = 4096;
	 * TargetDataLine line = getTargetDataLine(kDefaultFormat);
	 * line.open(kDefaultFormat, buffSize);
	 * 
	 * ByteArrayOutputStream out = new ByteArrayOutputStream(); int
	 * numBytesRead; byte[] data = new byte[line.getBufferSize() / 5];
	 * line.start(); while(true){ numBytesRead = line.read(data, 0,
	 * data.length); buff.write(data, 0, numBytesRead); }
	 * 
	 * } catch (Exception e){ System.out.println(e.toString()); } }
	 */

	/*
	 * public static void recordToFile(File file, int length){ try { int
	 * buffSize = 4096; TargetDataLine line = getTargetDataLine(kDefaultFormat);
	 * line.open(kDefaultFormat, buffSize);
	 * 
	 * ByteArrayOutputStream out = new ByteArrayOutputStream(); int
	 * numBytesRead; byte[] data = new byte[line.getBufferSize() / 5];
	 * line.start(); for(int i=0; i < length; i++) { numBytesRead =
	 * line.read(data, 0, data.length); out.write(data, 0, numBytesRead); }
	 * line.drain(); line.stop(); line.close();
	 * 
	 * writeWav(file, out.toByteArray(), kDefaultFormat);
	 * 
	 * } catch (Exception e){ System.out.println(e.toString()); } }
	 */

	/*
	 * public static TargetDataLine getTargetDataLine(AudioFormat format) throws
	 * LineUnavailableException { DataLine.Info info = new
	 * DataLine.Info(TargetDataLine.class, format); if
	 * (!AudioSystem.isLineSupported(info)) { throw new
	 * LineUnavailableException(); } return (TargetDataLine)
	 * AudioSystem.getLine(info); }
	 * 
	 * public static SourceDataLine getSourceDataLine(AudioFormat format) throws
	 * LineUnavailableException { DataLine.Info info = new
	 * DataLine.Info(SourceDataLine.class, format); if
	 * (!AudioSystem.isLineSupported(info)) { throw new
	 * LineUnavailableException(); } return (SourceDataLine)
	 * AudioSystem.getLine(info); }
	 */

	public static void encodeFileToWav(File inputFile, File outputFile)
			throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Encoder.encodeStream(new FileInputStream(inputFile), baos);
		// patched out for Android
		// writeWav(outputFile, baos.toByteArray());
	}

	public static void performData(byte[] data) throws IOException {

		PlayThread p = new PlayThread(data);

	}

	public static void performArray(byte[] array, boolean compress, boolean fec)
			throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		if (compress)
			array = compress(array);

		if (fec)
			array = applyFECEncoding(array);

		Encoder.encodeStream(new ByteArrayInputStream(array), baos);
		performData(baos.toByteArray());
	}

	/**
	 * Returns a compressed version of the provided byte array.
	 * 
	 * @param bytes
	 *            an array of uncompressed bytes.
	 * @return an array containing the compressed bytes.
	 */
	public static byte[] compress(byte[] bytes) {
		Deflater deflater = new Deflater(Constants.COMPRESSION_LEVEL);
		deflater.setInput(bytes);
		deflater.finish();

		ByteArrayOutputStream baos = new ByteArrayOutputStream(bytes.length);
		byte[] buf = new byte[4 * 1024];
		while (!deflater.finished()) {
			int byteCount = deflater.deflate(buf);
			baos.write(buf, 0, byteCount);
		}
		deflater.end();

		byte[] compressed = baos.toByteArray();

		return compressed;
	}

	/**
	 * Returns an uncompressed version of the provided byte array.
	 * 
	 * @param bytes
	 *            an array of compressed bytes.
	 * @return an array containing the uncompressed bytes.
	 */
	public static byte[] decompress(byte[] bytes) throws DataFormatException {
		Inflater inflater = new Inflater();
		inflater.setInput(bytes);

		ByteArrayOutputStream baos = new ByteArrayOutputStream(bytes.length);
		byte[] buf = new byte[4 * 1024];
		while (!inflater.finished()) {
			int byteCount = inflater.inflate(buf);
			baos.write(buf, 0, byteCount);
		}
		inflater.end();

		byte[] decompressed = baos.toByteArray();

		return decompressed;
	}

	/**
	 * Applies forward error correction encoding to an array of bytes.
	 * 
	 * @param bytes
	 *            an array of bytes to encode with FEC.
	 * @return an array list of chunks containing the data's FEC symbols as
	 *         payloads.
	 */
	public static byte[] applyFECEncoding(byte[] bytes) {
		// the total length in bytes of the data to be encoded
		int dataLength = bytes.length;

		// apply forward error correction encoding
		FECParameters fecParams = FECParameters.deriveParameters(dataLength,
				Constants.FEC_PAYLOAD_BYTES,
				Constants.FEC_MAX_DECODING_BLOCK_BYTES);
		ArrayDataEncoder fecDataEncoder = OpenRQ.newEncoder(bytes, fecParams);
		ByteArrayOutputStream baos = new ByteArrayOutputStream(dataLength);
		for (SourceBlockEncoder sourceBlockEncoder : fecDataEncoder
				.sourceBlockIterable()) {
			// encode the fec source block source packets
			for (EncodingPacket packet : sourceBlockEncoder
					.sourcePacketsIterable()) {
				baos.write(packet.asArray(), 0, packet.numberOfSymbols());
			}

			// number of repair symbols
			// (e.g. the number may depend on a channel loss rate)
			int numRepairSymbols = (int) Math.ceil(sourceBlockEncoder
					.numberOfSourceSymbols() * Constants.FEC_DEGREE_REPAIR);

			// encode the fec source block repair packets
			for (EncodingPacket packet : sourceBlockEncoder
					.repairPacketsIterable(numRepairSymbols)) {
				baos.write(packet.asArray(), 0, packet.numberOfSymbols());
			}
		}

		return baos.toByteArray();
	}
}
