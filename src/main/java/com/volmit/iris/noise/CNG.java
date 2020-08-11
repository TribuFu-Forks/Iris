package com.volmit.iris.noise;

import java.util.List;

import com.volmit.iris.util.IrisInterpolation;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.NoiseInjector;
import com.volmit.iris.util.RNG;

public class CNG {
	public static long hits = 0;
	public static long creates = 0;
	public static final NoiseInjector ADD = (s, v) -> new double[] { s + v, 1 };
	public static final NoiseInjector SRC_SUBTRACT = (s, v) -> new double[] { s - v < 0 ? 0 : s - v, -1 };
	public static final NoiseInjector DST_SUBTRACT = (s, v) -> new double[] { v - s < 0 ? 0 : s - v, -1 };
	public static final NoiseInjector MULTIPLY = (s, v) -> new double[] { s * v, 0 };
	public static final NoiseInjector MAX = (s, v) -> new double[] { Math.max(s, v), 0 };
	public static final NoiseInjector MIN = (s, v) -> new double[] { Math.min(s, v), 0 };
	public static final NoiseInjector SRC_MOD = (s, v) -> new double[] { s % v, 0 };
	public static final NoiseInjector SRC_POW = (s, v) -> new double[] { Math.pow(s, v), 0 };
	public static final NoiseInjector DST_MOD = (s, v) -> new double[] { v % s, 0 };
	public static final NoiseInjector DST_POW = (s, v) -> new double[] { Math.pow(v, s), 0 };
	private double scale;
	private double fscale;
	private KList<CNG> children;
	private CNG fracture;
	private NoiseGenerator generator;
	private final double opacity;
	private NoiseInjector injector;
	private RNG rng;
	private int oct;
	private double patch;
	private double up;
	private double down;
	private double power;

	public static CNG signature(RNG rng) {
		return signature(rng, NoiseType.SIMPLEX);
	}

	public static CNG signature(RNG rng, NoiseType t) {
		// @builder
		return new CNG(rng.nextParallelRNG(17), t, 1D, 3).scale(0.012)
				.fractureWith(new CNG(rng.nextParallelRNG(18), 1, 2).scale(0.018)
						.child(new CNG(rng.nextParallelRNG(19), 1, 2).scale(0.1))
						.fractureWith(new CNG(rng.nextParallelRNG(20), 1, 2).scale(0.15), 24), 44)
				.down(0.3).patch(2.5);
		// @done
	}

	public CNG(RNG random) {
		this(random, 1);
	}

	public CNG(RNG random, int octaves) {
		this(random, 1D, octaves);
	}

	public CNG(RNG random, double opacity, int octaves) {
		this(random, NoiseType.SIMPLEX, opacity, octaves);
	}

	public CNG(RNG random, NoiseType t, double opacity, int octaves) {
		creates++;
		this.oct = octaves;
		this.rng = random;
		power = 1;
		scale = 1;
		patch = 1;
		fscale = 1;
		fracture = null;
		generator = t.create(random.nextParallelRNG(33).lmax());
		this.opacity = opacity;
		this.injector = ADD;

		if (generator instanceof OctaveNoise) {
			((OctaveNoise) generator).setOctaves(octaves);
		}
	}

	public CNG child(CNG c) {
		if (children == null) {
			children = new KList<>();
		}

		children.add(c);
		return this;
	}

	@Deprecated
	public RNG nextRNG() {
		return getRNG().nextRNG();
	}

	public RNG getRNG() {
		return rng;
	}

	public CNG fractureWith(CNG c, double scale) {
		fracture = c;
		fscale = scale;
		return this;
	}

	public CNG scale(double c) {
		scale = c;
		return this;
	}

	public CNG patch(double c) {
		patch = c;
		return this;
	}

	public CNG up(double c) {
		up = c;
		return this;
	}

	public CNG down(double c) {
		down = c;
		return this;
	}

	public CNG injectWith(NoiseInjector i) {
		injector = i;
		return this;
	}

	public <T> T fit(T[] v, double... dim) {
		if (v.length == 0) {
			return null;
		}

		if (v.length == 1) {
			return v[0];
		}

		return v[fit(0, v.length - 1, dim)];
	}

	public <T> T fit(List<T> v, double... dim) {
		if (v.size() == 0) {
			return null;
		}

		if (v.size() == 1) {
			return v.get(0);
		}

		return v.get(fit(0, v.size() - 1, dim));
	}

	public int fit(int min, int max, double... dim) {
		if (min == max) {
			return min;
		}

		double noise = noise(dim);

		return (int) Math.round(IrisInterpolation.lerp(min, max, noise));
	}

	public int fitDouble(double min, double max, double... dim) {
		if (min == max) {
			return (int) Math.round(min);
		}

		double noise = noise(dim);

		return (int) Math.round(IrisInterpolation.lerp(min, max, noise));
	}

	public double fitDoubleD(double min, double max, double... dim) {
		if (min == max) {
			return min;
		}

		double noise = noise(dim);

		return IrisInterpolation.lerp(min, max, noise);
	}

	public int fitDoubleExponent(double min, double max, double exponent, double... dim) {
		if (min == max) {
			return (int) Math.round(min);
		}

		double noise = noise(dim);

		return (int) Math.round(IrisInterpolation.lerp(min, max, exponent == 1 ? noise : Math.pow(noise, exponent)));
	}

	public double noise(double... dim) {
		double f = fracture != null ? (fracture.noise(dim) - 0.5) * fscale : 0D;
		double x = dim.length > 0 ? dim[0] + f : 0D;
		double y = dim.length > 1 ? dim[1] - f : 0D;
		double z = dim.length > 2 ? dim[2] + f : 0D;
		double n = generator.noise(x * scale, y * scale, z * scale) * opacity;
		n = power != 1D ? Math.pow(n, power) : n;
		double m = 1;
		hits += oct;
		if (children == null) {
			return (n - down + up) * patch;
		}

		for (CNG i : children) {
			double[] r = injector.combine(n, i.noise(dim));
			n = r[0];
			m += r[1];
		}

		return ((n / m) - down + up) * patch;
	}

	public CNG pow(double power) {
		this.power = power;
		return this;
	}

	public CNG oct(int octaves) {
		oct = octaves;
		return this;
	}
}
