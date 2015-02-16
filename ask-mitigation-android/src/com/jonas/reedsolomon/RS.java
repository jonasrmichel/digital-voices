package com.jonas.reedsolomon;

/**
 * Reed Solomon Encoder/Decoder
 * 
 * Copyright Henry Minsky (hqm@alum.mit.edu) 1991-2009 (Ported to Java by Jonas
 * Michel 2012)
 * 
 * This is a direct port of RSCODE by Henry Minsky
 * http://rscode.sourceforge.net/
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * 
 * Commercial licensing is available under a separate license, please contact
 * author for details.
 * 
 * Source code is available at
 * http://code.google.com/p/mobile-acoustic-modems-in-action/
 * 
 */

public class RS {
	/*
	 * Below is parityBytes, the only parameter you should have to
	 * modify.
	 *	  
	 * It is the number of parity bytes that will be appended to
	 * your data to create a codeword.
	 * 
	 * In general, with E errors, and K erasures, you will need
	 * 2E + K bytes of parity to be able to correct the codeword
	 * back to recover the original message data.
	 *
	 * You could say that each error 'consumes' two bytes of the parity,
	 * whereas each erasure 'consumes' one byte.
	 *	
	 * Note that the maximum codeword size is 255, so the
	 * sum of your message length plus parity should be less than
	 * or equal to this maximum limit.
	 * 
	 * In practice, you will get slow error correction and decoding
	 * if you use more than a reasonably small number of parity bytes.
	 * (say, 10 or 20)
	 */
	private int parityBytes;
	
	/* maximum degree of various polynomials */
	private int maxDeg;

	/* Berlekamp algorithms and supporting tables. */
	Berlekamp berlekamp;

	/* Encoder parity bytes */
	private int pBytes[];

	/* Decoder syndrome bytes */
	private int synBytes[];

	/* generator polynomial */
	private int genPoly[];

	public RS(int parityBytes) {
		this.parityBytes = parityBytes;
		maxDeg = parityBytes * 2;

		pBytes = new int[maxDeg];
		synBytes = new int[maxDeg];
		genPoly = new int[maxDeg * 2];

		/* Initialize the galois field arithmetic tables */
		Galois.init_galois_tables();
		
		/* Initialize the Berlekamp tables. */
		berlekamp = new Berlekamp(parityBytes, synBytes);

		/* Compute the encoder generator polynomial */
		compute_genpoly(parityBytes, genPoly);
	}

	private void zero_fill_from(byte[] buf, int from, int to) {
		int i;
		for (i = from; i < to; i++)
			buf[i] = 0;
	}

	/* debugging routines */
	private void print_parity() {
		int i;
		System.out.print("Parity Bytes: ");
		for (i = 0; i < parityBytes; i++)
			System.out.print("[" + i + "]: 0x" + Integer.toHexString(pBytes[i])
					+ ", ");
		System.out.println();
	}

	private void print_syndrome() {
		int i;
		System.out.print("Syndrome Bytes: ");
		for (i = 0; i < parityBytes; i++)
			System.out.print("[" + i + "]: 0x"
					+ Integer.toHexString(synBytes[i]) + ", ");
		System.out.println();
	}

	/* Append the parity bytes onto the end of the message */
	private void build_codeword(byte[] msg, int nbytes, byte[] codeword) {
		int i;

		for (i = 0; i < nbytes; i++)
			codeword[i] = msg[i];

		for (i = 0; i < parityBytes; i++) {
			codeword[i + nbytes] = (byte) pBytes[parityBytes - 1 - i];
		}
	}

	/**********************************************************
	 * Reed Solomon Decoder
	 * 
	 * Computes the syndrome of a codeword. Puts the results into the synBytes[]
	 * array.
	 */

	public void decode_data(byte[] codeword, int nbytes) {
		int i, j, sum;
		for (j = 0; j < parityBytes; j++) {
			sum = 0;
			for (i = 0; i < nbytes; i++) {
				// !!!: byte-ify
				sum = (0xFF & (int) codeword[i])
						^ Galois.gmult(Galois.gexp[j + 1], sum);
			}
			synBytes[j] = sum;
		}
	}

	/* Check if the syndrome is zero */
	public int check_syndrome() {
		int i, nz = 0;
		for (i = 0; i < parityBytes; i++) {
			if (synBytes[i] != 0) {
				nz = 1;
				break;
			}
		}
		return nz;
	}

	public void debug_check_syndrome() {
		int i;

		for (i = 0; i < 3; i++) {
			System.out.println(" inv log S["
					+ i
					+ "]/S["
					+ (i + 1)
					+ "] = "
					+ Galois.glog[Galois.gmult(synBytes[i],
							Galois.ginv(synBytes[i + 1]))]);
		}
	}

	/*
	 * Create a generator polynomial for an n byte RS code. The coefficients are
	 * returned in the genPoly arg. Make sure that the genPoly array which is
	 * passed in is at least n+1 bytes long.
	 */

	private void compute_genpoly(int nbytes, int genpoly[]) {
		int i;
		int tp[] = new int[256], tp1[] = new int[256];

		/* multiply (x + a^n) for n = 1 to nbytes */
		berlekamp.zero_poly(tp1);
		tp1[0] = 1;

		for (i = 1; i <= nbytes; i++) {
			berlekamp.zero_poly(tp);
			tp[0] = Galois.gexp[i]; /* set up x+a^n */
			tp[1] = 1;

			berlekamp.mult_polys(genpoly, tp, tp1);
			berlekamp.copy_poly(tp1, genpoly);
		}
	}

	/*
	 * Simulate a LFSR with generator polynomial for n byte RS code. Pass in a
	 * pointer to the data array, and amount of data.
	 * 
	 * The parity bytes are deposited into pBytes[], and the whole message and
	 * parity are copied to dest to make a codeword.
	 */

	public void encode_data(byte[] msg, int nbytes, byte[] codeword) {
		int i;
		int LFSR[] = new int[parityBytes + 1], dbyte, j;

		for (i = 0; i < parityBytes + 1; i++)
			LFSR[i] = 0;

		for (i = 0; i < nbytes; i++) {
			// !!!: byte-ify
			dbyte = ((msg[i] ^ LFSR[parityBytes - 1]) & 0xFF);
			for (j = parityBytes - 1; j > 0; j--) {
				LFSR[j] = LFSR[j - 1] ^ Galois.gmult(genPoly[j], dbyte);
			}
			LFSR[0] = Galois.gmult(genPoly[0], dbyte);
		}

		for (i = 0; i < parityBytes; i++)
			pBytes[i] = LFSR[i];

		build_codeword(msg, nbytes, codeword);
	}

	public int correct_errors_erasures(byte[] codeword, int csize,
			int nerasures, int erasures[]) {
		return berlekamp.correct_errors_erasures(codeword, csize, nerasures,
				erasures);
	}
}
