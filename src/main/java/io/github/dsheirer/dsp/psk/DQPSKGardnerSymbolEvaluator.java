/*******************************************************************************
 * sdr-trunk
 * Copyright (C) 2014-2018 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by  the Free Software Foundation, either version 3 of the License, or  (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful,  but WITHOUT ANY WARRANTY; without even the implied
 * warranty of  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License  along with this program.
 * If not, see <http://www.gnu.org/licenses/>
 *
 ******************************************************************************/
package io.github.dsheirer.dsp.psk;

import io.github.dsheirer.dsp.symbol.Dibit;
import io.github.dsheirer.sample.complex.Complex;
import org.apache.commons.math3.util.FastMath;

public class DQPSKGardnerSymbolEvaluator implements IPSKSymbolEvaluator<Dibit>
{
    private static final Complex ROTATE_FROM_PLUS_135 = Complex.fromAngle(-3.0 * FastMath.PI / 4.0);
    private static final Complex ROTATE_FROM_PLUS_45 = Complex.fromAngle(-1.0 * FastMath.PI / 4.0);
    private static final Complex ROTATE_FROM_MINUS_45 = Complex.fromAngle(1.0 * FastMath.PI / 4.0);
    private static final Complex ROTATE_FROM_MINUS_135 = Complex.fromAngle(3.0 * FastMath.PI / 4.0);

    private float mPhaseError = 0.0f;
    private float mTimingError = 0.0f;
    private Dibit mSymbolDecision = Dibit.D00_PLUS_1;
    private Complex mPreviousSymbol = new Complex(0, 0);
    private Complex mEvaluationSymbol = new Complex(0, 0);

    /**
     * Differential QPSK Decision-directed symbol phase and timing error detector and symbol decision slicer.
     *
     * Symbol decision is based on the closest reference quadrant for the sampled symbol.
     *
     * Phase error is calculated as the angular distance of the sampled symbol from the reference symbol.
     *
     * Timing error is calculated using the Gardner method by comparing the previous symbol to the current symbol and
     * amplifying the delta between the two using the intra-symbol sample to form the timing error.
     */
    public DQPSKGardnerSymbolEvaluator()
    {
    }

    /**
     * Sets the middle and current symbols to be evaluated for phase and timing errors and to determine the
     * transmitted symbol relative to the closest reference symbol.  After invoking this method, you can access the
     * phase and timing errors and the symbol decision via their respective accessor methods.
     *
     * Phase and timing error values are calculated by first determining the symbol and then calculating the phase
     * and timing errors relative to the reference symbol.  The timing error is corrected with the appropriate sign
     * relative to the angular vector rotation so that the error value indicates the correct error direction.
     *
     * @param middle interpolated differentially-decoded sample that falls midway between previous/current symbols
     * @param current interpolated differentially-decoded symbol
     */
    public void setSymbols(Complex middle, Complex current)
    {
        //Gardner timing error calculation
        float errorInphase = (mPreviousSymbol.inphase() - current.inphase()) * middle.inphase();
        float errorQuadrature = (mPreviousSymbol.quadrature() - current.quadrature()) * middle.quadrature();
        mTimingError = normalize(errorInphase + errorQuadrature, .3f);

        //Store the current symbol to use in the next symbol calculation
        mPreviousSymbol.setValues(current);

        //Phase error and symbol decision calculations ...
        mEvaluationSymbol.setValues(current);

        if(mEvaluationSymbol.quadrature() > 0.0f)
        {
            if(mEvaluationSymbol.inphase() > 0.0f)
            {
                mSymbolDecision = Dibit.D00_PLUS_1;
                mEvaluationSymbol.multiply(ROTATE_FROM_PLUS_45);
            }
            else
            {
                mSymbolDecision = Dibit.D01_PLUS_3;
                mEvaluationSymbol.multiply(ROTATE_FROM_PLUS_135);
            }

        }
        else
        {
            if(mEvaluationSymbol.inphase() > 0.0f)
            {
                mSymbolDecision = Dibit.D10_MINUS_1;
                mEvaluationSymbol.multiply(ROTATE_FROM_MINUS_45);
            }
            else
            {
                mSymbolDecision = Dibit.D11_MINUS_3;
                mEvaluationSymbol.multiply(ROTATE_FROM_MINUS_135);
            }
        }

        //Since we've rotated the error symbol back to 0 radians, the quadrature value closely approximates the
        //arctan of the error angle relative to 0 radians and this provides our error value
        mPhaseError = normalize(-mEvaluationSymbol.quadrature(), 0.3f);
    }

    /**
     * Constrains value to the range of ( -maximum <> maximum )
     */
    public static float clip(float value, float maximum)
    {
        if(value > maximum)
        {
            return maximum;
        }
        else if(value < -maximum)
        {
            return -maximum;
        }

        return value;
    }

    /**
     * Constrains timing error to +/- the maximum value and corrects any
     * floating point invalid numbers
     */
    private float normalize(float error, float maximum)
    {
        if(Float.isNaN(error))
        {
            return 0.0f;
        }
        else
        {
            return clip(error, maximum);
        }
    }


    /**
     * Phase error of the symbol relative to the nearest reference symbol.
     *
     * @return phase error in radians of distance from the reference symbol.
     */
    @Override
    public float getPhaseError()
    {
        return mPhaseError;
    }

    /**
     * Timing error of the symbol relative to the nearest reference symbol.
     *
     * @return timing error in radians of angular distance from the reference symbol recognizing that the symbol
     * originates at zero radians and rotates toward the intended reference symbol, therefore the error value indicates
     * if the symbol was sampled early (-) or late (+) relative to the reference symbol.
     */
    @Override
    public float getTimingError()
    {
        return mTimingError;
    }

    /**
     * Reference symbol that is closest to the transmitted/sampled symbol.
     */
    @Override
    public Dibit getSymbolDecision()
    {
        return mSymbolDecision;
    }
}
