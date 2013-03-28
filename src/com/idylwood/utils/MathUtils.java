/*
 * ====================================================
 * Copyright (C) 2013 by Idylwood Technologies, LLC. All rights reserved.
 *
 * Developed at Idylwood Technologies, LLC.
 * Permission to use, copy, modify, and distribute this
 * software is freely granted, provided that this notice 
 * is preserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * The License should have been distributed to you with the source tree.
 * If not, it can be found at
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Author: Charles Cooper
 * Date: 2013
 * ====================================================
 */
package com.idylwood.utils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Arrays;

// Final so as to avoid vtables
// This class contains a bunch of numerical methods which are intended to be fast and precise.
// Several of the methods come in 'fast', 'normal' and 'slow' versions to give the end user
// more control over the trade-off between accuracy and speed.
// Most of the methods are marked final as a matter of style
// (and also intended to avoid vtable lookups and let the JIT inline better).
public final class MathUtils {
	// remove public instantiation
	private MathUtils() {}

	// TODO put this thing somewhere else
	public static class LinearRegression {
		public final double intercept, slope;
		public LinearRegression(final double intercept, final double slope) {
			this.intercept = intercept; this.slope = slope;
		}
	}

	// TODO get rid of garbage
	public static final LinearRegression regress(final double [] x, final double [] y)
	{
		final double intercept, slope;
		final double xMean = mean(x);
		final double yMean = mean(y);
		final double [] xCentered = shift(x,-xMean);
		final double [] yCentered = shift(y,-yMean);
		slope = sum( multiply(xCentered,yCentered) ) / sum (pow(xCentered,2));
		intercept = yMean - slope * xMean;
		return new LinearRegression(intercept,slope);
	}

	// Faster and more correct (round away from zero instead of round towards +infinity) than java.lang.Math.round.
	private static final long ONE_HALF = Double.doubleToRawLongBits(0.5); // bits of 0.5
	public final static long round(final double d)
	{
		final long l = ONE_HALF | sign(d); // equivalent to d < 0 ? -0.5 : 0.5;
		return (long)(d + Double.longBitsToDouble(l));
	}
	// returns 1L<<63 if d < 0 and 0 otherwise
	public static final long sign(final double d)
	{
		return Double.doubleToRawLongBits(d) & Long.MIN_VALUE;
	}

	public final static long[] round(final double[] d)
	{
		final long[] ret = new long[d.length];
		for (int i = d.length; 0!=i--;)
			ret[i] = round(d[i]);
		return ret;
	}

	// TODO do we need more InPlace methods? they are significantly faster because of no allocation/copying overhead
	public final static void roundInPlace(final double[] d)
	{
		for (int i = d.length; 0!=i--;)
			d[i] = (double)round(d[i]);
	}

	public final static double roundToCent(final double d)
	{
		return round(d*100.0) / 100.0;
	}

	// numerically stable calculation of mean
	public final static double mean(final double [] values)
	{
		return sum(values) / values.length;
	}

	public final static double meanFast(final double[] values)
	{
		return sumFast(values) / values.length;
	}

	public final static double meanSlow(final double[] values)
	{
		return sumSlow(values) / values.length;
	}

	public final static double max(double... values)
	{
		if (values.length==0) return Double.NaN;
		double ret = values[0];
		for (int i = values.length; i--!=0;)
			if (values[i] > ret)
				ret = values[i];
		return ret;
	}

	public final static int max(int... values)
	{
		if (values.length==0) return Integer.MIN_VALUE;
		int ret = values[0];
		for (int i = values.length; i--!=0;)
			if (values[i] > ret)
				ret = values[i];
		return ret;
	}

	// not for production code
	static final double multiplyAndSumSlow(final double[]values, final double scale)
	{
		BigDecimal ret = new BigDecimal(0);
		for (double x : values)
			ret = ret.add(new BigDecimal(x),MathContext.UNLIMITED);
		return ret.multiply(new BigDecimal(scale),MathContext.UNLIMITED).doubleValue();
	}
	// No side effects.
	// TODO implement without garbage
	static public final double multiplyAndSum(final double[]d, final double scale)
	{
		return sum(scale(d,scale));
	}

	// Note: this seems to be less accurate than multiplyAndSum
	static public final double multiplyAndSumFast(final double[]d,final double scale)
	{
		return scale*sum(d);
	}

	// for comparison purposes
	static public final double varianceApache(final double[] values)
	{
		return new org.apache.commons.math3.stat.descriptive.moment.Variance().evaluate(values);
	}

	// Alternative implementation of variance. Not sure which one is more precise.
	static public final double varianceTwo(final double [] values)
	{
		final long n = values.length;
		final long n1 = values.length - 1;
		final double sumOfSquares = sum(pow(values,2)) / n1;
		final double squareOfSum = Math.pow(sum(values),2) / (n*n1);
		return sumOfSquares - squareOfSum;
	}

	// if the mean is precalculated, no point in calculating it again!
	static public final double variance(final double [] values, final double mean)
	{
		final double [] squares = pow(shift(values,-mean),2);
		return sum(squares) / (squares.length - 1);
	}

	static public final double stdev(final double [] values, final double mean)
	{
		return Math.sqrt(variance(values,mean));
	}
	static public final double stdev(final double[] values)
	{
		final double mean = mean(values);
		return stdev(values,mean);
	}

	// Numerically stable calculation of variance
	// whose accuracy doesn't suffer for large n
	// IIRC this matches with varianceApache in tests
	// but it is much faster.
	static public final double variance(final double [] values)
	{
		return variance(values,mean(values));
	}

	// calculation of variance which is somewhat numerically stable
	public static final double variancePopulation(final double [] values)
	{
		final double mean = mean(values);
		final double [] centered = shift(values,-mean);
		final double [] squares = pow(centered,2);
		return sum(squares) / squares.length;
	}

	// theoretically same as R's 'diff' function
	final public static double [] diff(final double[] data)
	{
		double [] ret = new double[data.length - 1];
		for (int i = data.length - 1; i--!=0; )
			ret[i] = data[i+1] - data[i];
		return ret;
	}

	// This will not throw an exception if the arrays are not of equal length,
	// it will return an array whose length is the smaller of the two array lengths
	final public static double [] subtract(final double[] first, final double[] second)
	{
		final int len = Math.min(first.length,second.length);
		final double ret[] = new double[len];
		for (int i = len; i-- != 0;)
			ret[i] = first[i] - second[i];
		return ret;
	}

	final public static double [] add(final double[] first, final double[] second)
	{
		final int len = Math.min(first.length,second.length);
		final double ret[] = new double[len];
		for (int i = len; i--!=0;)
			ret[i] = first[i]+second[i];
		return ret;
	}

	// Returns newly allocated array.
	final public static double [] multiply(final double [] first, final double[] second)
	{
		final int len = first.length;
		if (len!=second.length)
			throw new ArrayIndexOutOfBoundsException("Tried to multiply two vectors of unequal length!");
		final double ret[] = new double[len];
		for (int i = len; i--!=0;)
			ret[i] = first[i]*second[i];
		return ret;
	}

	// takes the log of every element of the data
	final public static double [] log(final double[] data)
	{
		final double [] ret = new double[data.length];
		for (int i = data.length; i-- != 0; )
			ret[i] = Math.log(data[i]);
		return ret;
	}

	// infinite precision but slow and there is no bound on how
	// much memory it will need
	final public static double meanArbPrec(final double data[])
	{	BigDecimal mean = new BigDecimal(0);
		for (double x : data)
			mean = mean.add(new BigDecimal(x),MathContext.UNLIMITED);
		mean = mean.divide(new BigDecimal(data.length),MathContext.UNLIMITED);
		return mean.doubleValue();
	}

	// funnily enough, even though this uses BigDecimals,
	// it has a bug in it and spits out wrong answers sometimes.
	// (I think the bug is in how it handles division and
	// repeating rational numbers
	private final static double varianceSlow(final double[] data)
	{
		BigDecimal mean = new BigDecimal(0);
		for (double x : data)
			mean = mean.add(new BigDecimal(x),MathContext.UNLIMITED);
		mean = mean.divide(new BigDecimal(data.length),MathContext.UNLIMITED);
		//mean = new BigDecimal(mean(data));
		BigDecimal ret = new BigDecimal(0);
		for (double x : data)
		{
			//BigDecimal summand = ret.add(new BigDecimal(x),MathContext.UNLIMITED);
			BigDecimal summand = new BigDecimal(x).subtract(mean,MathContext.UNLIMITED);
			ret = ret.add(summand.pow(2));
		}
		ret = ret.divide(new BigDecimal(data.length - 1),MathContext.DECIMAL128);
		return ret.doubleValue();
	}

	// numerically stable calculation of standard deviation
	public static final double stdevPopulation(final double [] values)
	{
		return Math.sqrt(variance(values));
	}

	// Takes all the values to the power of exp
	public static final double [] pow(final double[] values, final double exp)
	{
		final double[] ret = new double[values.length];
		for (int i = values.length; i--!=0; )
			ret[i] = Math.pow(values[i],exp);
		return ret;
	}

	// Returns a newly allocated array with all the values
	// of the original array added to the shift.
	// TODO maybe rename this 'add'?
	public static final double [] shift(final double[] values, final double constant)
	{
		final double ret[] = new double[values.length];
		for (int i = values.length; i--!=0; )
			ret[i] = values[i] + constant;
		return ret;
	}

	// Returns a newly allocated array with all the values
	// of the original array multiplied by the scale.
	// TODO maybe rename this 'multiply'?
	public static final double [] scale(double[] values, double scale)
	{
		double [] ret = new double[values.length];
		for (int i = values.length; i--!=0;)
			ret[i] = values[i]*scale;
		return ret;
	}

	// numerically precise implementation of sum
	// Optimized version of an implementation of Schewchuk's algorithm
	// which keeps full precision by keeping O(n) space
	// for the error, unlike Kahan's algorithm which keeps O(1) space.
	// The tradeoff is that this is fully precise, but Kahan's algorithm
	// is almost always precise anyways. It is about 12x slower
	// than the naive implementation, but in turn about 10x faster than calculating
	// the sum to full precision and then truncating.
	public final static double sumSlow(double... values)
	{
		final double[] partials = new double[values.length];
		int size = 0;
		for (double x : values)
		{
			int i = 0;
			for (int j = 0; j < size; ++j) // size not necessarily == partials.length
			{
				double y = partials[j];

				if (abs(x) < abs(y))
				{
					final double tmp = x;
					x = y;
					y = tmp;
				}
				double hi = x + y;
				double lo = y - (hi - x);
				if (lo != 0.0)
					partials[i++] = lo;
				x = hi;
			}
			if (i < size)
			{
				partials[i] = x;
				Arrays.fill(partials,i+1,size,0);
			}
			else
			{
				partials[size++] = x;
			}
		}
		double sum = 0;
		for (double d : partials) sum += d;
		return sum;
	}

	// debugger function, not really needed
	static void logTime(String msg)
	{
		com.idylwood.yahoo.YahooFinance.logTime(msg);
	}

	// returns newly allocated array of length len
	// whose elements are doubles between 0.0 and 1.0
	// generated by java.lang.Math.random()
	public static final double[] random(int len)
	{
		final double ret[] = new double[len];
		for (int i = len; i-- != 0; )
			ret[i] = Math.random();
		return ret;
	}

	// returns newly allocated array with same length as input
	// elements are max(input,0)
	final public static double[] positivePart(final double[] data)
	{
		final double [] ret = new double[data.length];
		for (int i = data.length; i--!=0;)
			ret[i] = Math.max(data[i],0.0);
		return ret;
	}

	// returns newly allocated array
	// elements are min(input,-0)
	final public static double [] negativePart(final double[] data)
	{
		final double [] ret = new double[data.length];
		for (int i = data.length; 0!=i--;)
			ret[i] = Math.min(data[i],-0.0);
		return ret;
	}

	// for comparison with apache commons. not for production code!
	static final double apacheSum(final double[] values)
	{
		return new org.apache.commons.math3.stat.descriptive.summary.Sum().evaluate(values);
	}

	public static final double abs(final double d)
	{
		return Double.longBitsToDouble(Long.MAX_VALUE & Double.doubleToRawLongBits(d));
	}
	public static final float abs(final float f)
	{
		return Float.intBitsToFloat(Integer.MAX_VALUE & Float.floatToRawIntBits(f));
	}
	public static final long abs(final long l)
	{
		final long sign = l>>>63;
		return (l^(~sign+1)) + sign;
	}
	public static final int abs(final int i)
	{
		final int sign = i>>>63;
		return (i^(~sign+1)) + sign;
	}

	// Implementation of sum which is both more numerically
	// stable _and faster_ than the naive implementation
	// which is used in all standard numerical libraries I know of:
	// Colt, OpenGamma, Apache Commons Math, EJML.
	//
	// Implementation uses variant of Kahan's algorithm keeping a running error
	// along with the accumulator to try to cancel out the error at the end.
	// This is much faster than Schewchuk's algorithm but not
	// guaranteed to be perfectly precise
	// In most cases, however, it is just as precise.
	public static final double sum(final double... values)
	{
		double sum = 0;
		double err = 0;
		final int unroll = 6; // empirically it doesn't get much better than this
		final int len = values.length - values.length%unroll;

		// unroll the loop. due to IEEE 754 restrictions
		// the JIT shouldn't be allowed to unroll it dynamically, so it's
		// up to us to do it by hand ;)
		int i = 0;
		for (; i < len; i+=unroll)
		{
			final double val = values[i] + values[i+1]
				+ values[i+2] + values[i+3]
				+ values[i+4] + values[i+5];
			final double hi = sum + val;
			err += (hi - sum) - val;
			sum = hi;
		}
		for (; i < values.length; i++)
		{
			final double val = values[i];
			final double hi = sum + val;
			err += (hi - sum) - val;
			sum = hi;
		}
		return sum - err;
	}

	// Naive implementation of sum which is faster than MathUtils.sum().
	// Generally exhibits rounding error which grows with the length of the sum
	// Note that it may not agree with other implementations
	// due to optimizations which change the order of iteration
	// which can affect the rounding error.
	// It is O(n) in the length of the array to be summed.
	public static final double sumFast(final double... values)
	{
		double ret = 0;
		// unroll the loop since the JIT shouldn't
		final int unroll = 4; // empirically unrolling more than 2 doesn't help much
		final int len = values.length - values.length%unroll;
		int i = 0;
		for (; i < len; i+=unroll)
			ret += values[i] + values[i+1] + values[i+2] + values[i+3];
		for (; i < values.length; i++)
			ret += values[i];
		return ret;
	}

	// Numerically precise implementation of sum.
	// Uses java.math.BigDecimal to internally keep an arbitrary
	// precision accumulator and then truncates at the end of the sum.
	// MathUtils.sumSlow(double[]), in addition to being about 10 times faster,
	// should (theoretically) return the same value as this method,
	// so this method shouldn't be used except as a sanity check.
	public static final double sumArbitraryPrecision(final double... values)
	{
		BigDecimal sum = new BigDecimal(0);
		for (int i = values.length; i-- != 0; )
			sum = sum.add(new BigDecimal(values[i]),MathContext.UNLIMITED);
		return sum.doubleValue();
	}

	static final boolean testSum (final double[] values)
	{
		return sum(values)==sumSlow(values);
	}

	public final static void printArray(double[] d)
	{
		System.out.print("[");
		for (int i = 0; i < d.length; ++i)
		{
			if (0!=i) System.out.print(",");
			System.out.print(d[i]);
		}
		System.out.println("]");
	}

	static final void compare(final String msg, final double d1, final double d2)
	{
		final double diff = d1-d2;
		System.out.println("Diff "+msg+":"+diff);
		System.out.println("Exponent:"+Math.getExponent(diff));
		System.out.println("Mantissa:"+mantissa(diff));
		System.out.println("Precision:"+precision(diff));
	}

	// Returns the number of bits required to represent d
	// by counting the number of trailing zeros in the mantissa.
	public static final int precision(final double d)
	{
		final long l = Double.doubleToLongBits(d);
		return Math.max(0,53 - Long.numberOfTrailingZeros(l));
	}

	// Implementation which uses a BigDecimal for the accumulator
	// and so should have infinite precision.
	// Used for sanity checking.
	static final double linearCombinationArbitraryPrecision(final double []x, final double[]y)
	{
		final int len = Math.min(x.length,y.length);
		//final double [][] ret = new double[len][2];
		BigDecimal ret = new BigDecimal(0);
		for (int i = len; 0!=i--;)
		{
			BigDecimal product = new BigDecimal(x[i]).multiply(new BigDecimal(y[i]),MathContext.UNLIMITED);
			ret = ret.add(product,MathContext.UNLIMITED);
		}
		return ret.doubleValue();
	}

	// Numerically precise dot product. Keeps a running error along with the
	// accumulator. Equivalent to MathUtils.sum(MathUtils.multiply(x,y))
	// but much faster and with O(1) memory overhead.
	// O(n) with O(1) space.
	// Even faster than the naive implementation ;).
	public static final double linearCombination(final double[]x, final double[]y)
	{
		//if (true) return MathUtils.sum(MathUtils.multiply(x,y));
		if (x.length!=y.length)
			throw new ArrayIndexOutOfBoundsException("Tried to take a dot product of"
					+" two unequal length vectors!");
		final int unroll = 3; // don't blindly change this without changing the loop!
		final int len = x.length - x.length%unroll;
		double sum = 0;
		double err = 0;
		int i = 0;
		for (; i < len; i+= unroll)
		{
			// this line depends on unroll variable.
			final double prod = x[i]*y[i] + x[i+1]*y[i+1] + x[i+2]*y[i+2];
			final double hi = sum + prod;
			err += hi - sum - prod;
			sum = hi;
		}
		for (; i < x.length; i++)
		{
			final double prod = x[i]*y[i];
			final double hi = sum + prod;
			err += hi - sum - prod;
			sum = hi;
		}
		return sum - err;
	}

	public static final double normSquared(final double[]x)
	{
		return linearCombination(x,x);
	}

	public static final double norm(final double[] x)
	{
		return Math.sqrt(normSquared(x));
	}

	public static final double[] extractColumn(final double[][] matrix, final int idx)
	{
		final double[] ret = new double[matrix[0].length];
		for (int i = 0; i < matrix.length; i++)
			ret[i] = matrix[i][idx];
		return ret;
	}

	// TODO think about parallelizing this
	public static final double[][] matrixMultiply(final double[][] first, final double[][] second)
	{
		// i,j,k
		final int firstRows = first.length;
		final int firstCols = first[0].length;
		final int secondRows = second.length;
		final int secondCols = second[0].length;
		if (firstCols!=secondRows)
			throw new ArrayIndexOutOfBoundsException("Trying to multiply matrices of different dimensions?!");
		final double ret[][] = new double[firstRows][secondCols];
		for (int i = 0; i < secondCols; i++)
		// iterate over columns so we can maintain cache locality!
		{
			final double[] vector = extractColumn(second,i);
			for (int k = 0; k < firstRows; k++)
			{
				ret[k][i] = linearCombination(first[k],vector);
			}
		}
		return ret;
	}

	public static final double[][] matrixMultiplyFast(final double[][] first, final double[][] second)
	{
		final int firstRows = first.length;
		final int firstCols = first[0].length;
		final int secondRows = second.length;
		final int secondCols = second[0].length;
		if (firstCols!=secondRows)
			throw new ArrayIndexOutOfBoundsException("Trying to multiply matrices of different dimensions?!");
		final double ret[][] = new double[firstRows][secondCols];
		for (int i = 0; i < secondCols; i++)
		// iterate over columns so we can maintain cache locality!
		{
			final double[] vector = extractColumn(second,i);
			for (int k = 0; k < firstRows; k++)
			{
				ret[k][i] = linearCombinationFast(first[k],vector);
			}
		}
		return ret;
	}

	// Pre: matrix has m rows and n columns, and vector has n elements.
	// Note: no checking on the sizes of the inputs!
	// May throw ArrayIndexOutOfBoundsExceptions or other such
	// nasty things if you don't sanitize input!
	public static final double[] matrixMultiply(final double[][] matrix, final double[] vector)
	{
		// recall matrix is double[rows][cols], and matrix.length==rows
		final double ret[] = new double[matrix.length];
		for (int i = 0; i < matrix.length; i++)
			ret[i] = linearCombination(matrix[i],vector);
		return ret;
	}

	public static final double[] matrixMultiplyFast(final double[][] matrix, final double[] vector)
	{
		final double []ret = new double[matrix.length];
		for (int i = 0; i < matrix.length; ++i)
			ret[i] = linearCombinationFast(matrix[i],vector);
		return ret;
	}

	// Numerically precise dot product. Returns MathUtils.sumSlow(MathUtils.multiply(x,y));
	// O(n) time and O(n) space.
	// TODO make if faster by not allocating new array in multiply().
	static final double linearCombinationSlow(final double[]x,final double[]y)
	{
		return sumSlow(multiply(x,y));
	}

	// Faster but lower precision than linearCombination.
	// Naive implementation.
	static final double linearCombinationFast(final double []x, final double[]y)
	{
		if (x.length!=y.length)
			throw new ArrayIndexOutOfBoundsException("Dot product of vectors with different lengths!");
		double ret = 0;
		final int unroll = 3;
		final int len = x.length - x.length%unroll;
		int i = 0;
		for (; i < len; i+=unroll)
			ret+= x[i]*y[i] + x[i+1]*y[i+1] + x[i+2]*y[i+2];
		// get the terms at the end
		for (; i < x.length; i++)
			ret += x[i]*y[i];
		return ret;
	}

	static final double distance(final double[]p, final double[]q)
	{
		// TODO make this faster if needed, as it is it is going to loop like three times ;)
		return Math.sqrt(sum(pow(subtract(p,q),2)));
	}

	// Returns a double with the exponent and sign set to zero.
	// (Actually it sets the exponent to 1023,
	// which is the IEEE 754 exponent bias).
	static final double mantissa(final double d)
	{
		return abs(Math.scalb(d,-Math.getExponent(d)));
	}

	// Allocates new array which is reverse of the argument.
	// No side effects.
	public static final double[] reverse(final double[] data)
	{
		final double[] ret = new double[data.length];
		int center = data.length / 2;
		while (center--!=0)
		{
			final int left = center;
			final int right = data.length-center-1;
			ret[left] = data[right];
			ret[right] = data[left];
		}
		return ret;
	}

	// Allocates new array which is sorted version of argument.
	// O(n) in the allocation and then O(n log n) in the sort.
	// Behavior should be identical to calling Arrays.sort(data.clone())
	public static final double[] sort(final double[] data)
	{
		final double ret[] = copyOf(data);
		Arrays.sort(ret);
		return ret;
	}

	// Behavior is identical to calling data.clone() or Arrays.copyOf(data)
	// But can be up to 30% faster if the JIT doesn't optimize those functions
	public static final double[] copyOf(final double[]data)
	{
		final double ret[] = new double[data.length];
		for (int i = 0; i < data.length / 3; ++i)
		{
			final int x = 3*i;
			ret[x] = data[x];
			ret[x+1] = data[x+1];
			ret[x+2] = data[x+2];
		}
		// Don't care about extra copies if data.length%3==0
		ret[data.length-1] = data[data.length-1];
		ret[data.length-2] = data[data.length-2];
		return ret;
	}

	// Returns true if all the elements of the two arrays are equal
	// Throws NullPointerException if either x or y are null.
	public static final boolean equals(final double[]x, final double[]y)
	{
		final int len = x.length;
		if (y.length!=len)
			return false;
		for (int i = len; i--!=0;)
			if (x[i]!=y[i]) // TODO deal with NaNs?
				return false;
		return true;
	}

	public static final double[][] covariance(final double[][] data)
	{
		final int len = data.length;
		final double []means = new double[len]; // precalculate the means
		final double[][] ret = new double[len][len];
		for (int i = 0; i < len; i++)
		{
			means[i] = mean(data[i]);
			for (int j = 0; j <= i; j++)
			{
				final double d = sum(multiply(shift(data[i],-means[i]),shift(data[j],-means[j]))) / (len);
				ret[i][j] = d;
				ret[j][i] = d;
			}
		}
		return ret;
	}

	public static void main(String[] args)
	{
		final double [][]cov = covariance(new double[][]{{1,2,3},{0,1,1.5}});
		System.out.println(cov[1][1] - 0.5833333333333333703408);
		if (true) return;

		final int len = 1000*1000*100;
		final double[] data = random(len);
		final double fast,medium,slow;

		fast = sumFast(data);
		slow = sumSlow(data);
		medium = sum(data);
		compare("FS",fast,slow);
		compare("MS",medium,slow);
		compare("FM",fast,medium);

		sumSlow(data);
		logTime("slow");
		sumFast(data);
		logTime("fast");
		sum(data);
		logTime("mine");
	}
}


