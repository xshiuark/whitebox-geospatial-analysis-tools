/*
 * Copyright (C) 2011 Dr. John Lindsay <jlindsay@uoguelph.ca>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package plugins;

import java.util.Date;
import java.text.DecimalFormat;
import whitebox.geospatialfiles.WhiteboxRaster;
import whitebox.interfaces.WhiteboxPlugin;
import whitebox.interfaces.WhiteboxPluginHost;
/**
 *
 * @author Dr. John Lindsay <jlindsay@uoguelph.ca>
 */
public class LinearityIndex implements WhiteboxPlugin {

    private WhiteboxPluginHost myHost;
    private String[] args;

    public String getName() {
        return "LinearityIndex";
    }

    public String getDescriptiveName() {
    	return "Linearity Index";
    }

    public String getToolDescription() {
    	return "The LinearityIndex tool can be used to find patches that are "
                + "linear in shape, i.e. well-described by a straight line.";
    }

    public String[] getToolbox() {
    	String[] ret = { "PatchShapeTools" };
    	return ret;
    }

    
    public void setPluginHost(WhiteboxPluginHost host) {
        myHost = host;
    }

    private void showFeedback(String feedback) {
        if (myHost != null) {
            myHost.showFeedback(feedback);
        } else {
            System.out.println(feedback);
        }
    }

    private void returnData(Object ret) {
        if (myHost != null) {
            myHost.returnData(ret);
        }
    }

    private void updateProgress(String progressLabel, int progress) {
        if (myHost != null) {
            myHost.updateProgress(progressLabel, progress);
        } else {
            System.out.println(progressLabel + " " + progress + "%");
        }
    }

    private void updateProgress(int progress) {
        if (myHost != null) {
            myHost.updateProgress(progress);
        } else {
            System.out.print("Progress: " + progress + "%");
        }
    }
    
    public void setArgs(String[] args) {
        this.args = args.clone();
    }
    
    private boolean cancelOp = false;
    public void setCancelOp(boolean cancel) {
        cancelOp = cancel;
    }
    
    private void cancelOperation() {
        showFeedback("Operation cancelled.");
        updateProgress("Progress: ", 0);
    }
    
    private boolean amIActive = false;
    public boolean isActive() {
        return amIActive;
    }
    
    public void run() {
        amIActive = true;

        String inputHeader = null;
        String outputHeader = null;
        int col;
        int row;
        int numCols;
        int numRows;
        int a;
        int i;
        float progress;
        int range;
        boolean blnTextOutput = false;
        double z;

        if (args.length <= 0) {
            showFeedback("Plugin parameters have not been set.");
            return;
        }

        for (i = 0; i < args.length; i++) {
            if (i == 0) {
                inputHeader = args[i];
            } else if (i == 1) {
                outputHeader = args[i];
            } else if (i == 2) {
                blnTextOutput = Boolean.parseBoolean(args[i]);
            }
        }

        // check to see that the inputHeader and outputHeader are not null.
        if ((inputHeader == null) || (outputHeader == null)) {
            showFeedback("One or more of the input parameters have not been set properly.");
            return;
        }

        try {
            WhiteboxRaster image = new WhiteboxRaster(inputHeader, "r");

            numRows = image.getNumberRows();
            numCols = image.getNumberColumns();
            double noData = image.getNoDataValue();
            
            WhiteboxRaster output = new WhiteboxRaster(outputHeader, "rw", inputHeader, WhiteboxRaster.DataType.FLOAT, noData);
            output.setPreferredPalette("spectrum.pal");
            output.setDataScale(WhiteboxRaster.DataScale.CONTINUOUS);

            int minValue = (int)(image.getMinimumValue());
            int maxValue = (int)(image.getMaximumValue());
            range = maxValue - minValue;

            double[][] regressionData = new double[6][range + 1];
            double[] rSquare = new double[range + 1];
            double[][] totals = new double[3][range + 1];
            long[][] minRowAndCol = new long[2][range + 1];
            
            for (a = 0; a <= range; a++) {
                minRowAndCol[0][a] = Long.MAX_VALUE;
                minRowAndCol[1][a] = Long.MAX_VALUE;
            }
            
            
            updateProgress("Finding patch min row and columns:", 0);
            double[] data;
            for (row = 0; row < numRows; row++) {
                data = image.getRowValues(row);
                for (col = 0; col < numCols; col++) {
                    if (data[col] > 0) {
                        a = (int)(data[col] - minValue);
                        if (row < minRowAndCol[0][a]) {
                            minRowAndCol[0][a] = row;
                        }
                        if (col < minRowAndCol[1][a]) {
                            minRowAndCol[1][a] = col;
                        }
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (float) (100f * row / (numRows - 1));
                updateProgress("Finding patch min row and columns:", (int) progress);
            }
                 
            // Calculate the patch orientation.
            updateProgress("Calculating patch linearity:", 0);
            for (row = 0; row < numRows; row++) {
                data = image.getRowValues(row);
                for (col = 0; col < numCols; col++) {
                    if (data[col] > 0) {
                        a = (int)(data[col] - minValue);
                        regressionData[0][a]++; // N
                        regressionData[1][a] += (col - minRowAndCol[1][a]); // sigma X
                        regressionData[2][a] += (row - minRowAndCol[0][a]); // sigma Y
                        regressionData[3][a] += (col - minRowAndCol[1][a]) * (row - minRowAndCol[0][a]); // sigma XY
                        regressionData[4][a] += (col - minRowAndCol[1][a]) * (col - minRowAndCol[1][a]); // sigma Xsqr
                        regressionData[5][a] += (row - minRowAndCol[0][a]) * (row - minRowAndCol[0][a]); // sigma Ysqr
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (float) (100f * row / (numRows - 1));
                updateProgress("Calculating patch linearity:", (int) progress);
            }

            double sigmaX;
            double sigmaY;
            double N;
            double sigmaXY;
            double sigmaXsqr;
            double sigmaYsqr;
            double mean = 0;
            double Sxx, Syy, Sxy;
            //double radians2Deg = (180 / Math.PI);
            for (a = 0; a <= range; a++) {
                if (regressionData[0][a] > 1) {
                    N = regressionData[0][a];
                    sigmaX = regressionData[1][a];
                    mean = sigmaX / N;
                    sigmaY = regressionData[2][a];
                    sigmaXY = regressionData[3][a];
                    sigmaXsqr = regressionData[4][a];
                    sigmaYsqr = regressionData[5][a];
                    Sxx = (sigmaXsqr / N - mean * mean);
                    Syy = (sigmaYsqr / N - (sigmaY / N) * (sigmaY / N));
                    Sxy = (sigmaXY / N - (sigmaX * sigmaY) / (N * N));
                    z = (sigmaXsqr - mean * sigmaX);
                    if (Math.sqrt(Sxx * Syy) != 0) {
                        rSquare[a] = ((Sxy / Math.sqrt(Sxx * Syy)) * (Sxy / Math.sqrt(Sxx * Syy)));
                    }
                }
            }

            // Creat output image.
            updateProgress("Creating output image:", 0);
            for (row = 0; row < numRows; row++) {
                data = image.getRowValues(row);
                for (col = 0; col < numCols; col++) {
                    if (data[col] > 0) {
                        a = (int) (data[col] - minValue);
                        output.setValue(row, col, rSquare[a]);
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (float) (100f * row / (numRows - 1));
                updateProgress("Creating output image:", (int) progress);
            }
                
                
            output.addMetadataEntry("Created by the "
                    + getDescriptiveName() + " tool.");
            output.addMetadataEntry("Created on " + new Date());

            image.close();
            output.close();

            if (blnTextOutput) {
                DecimalFormat df;
                df = new DecimalFormat("0.0000");

                String retstr = "Linearity Index\nPatch ID\tLinearity";

                for (a = 0; a <= range; a++) {
                    if (regressionData[0][a] > 0) {
                        retstr = retstr + "\n" + (a + minValue) + "\t" + df.format(rSquare[a]);

                    }
                }


                returnData(retstr);
            }

            // returning a header file string displays the image.
            returnData(outputHeader);
            
            
        } catch (Exception e) {
            showFeedback(e.getMessage());
            showFeedback(e.getCause().toString());
        } finally {
            updateProgress("Progress: ", 0);
            // tells the main application that this process is completed.
            amIActive = false;
            myHost.pluginComplete();
        }
    }
}