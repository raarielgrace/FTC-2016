/*
Copyright (c) 2016 Robert Atkinson

All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted (subject to the limitations in the disclaimer below) provided that
the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.

Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or
other materials provided with the distribution.

Neither the name of Robert Atkinson nor the names of his contributors may be used to
endorse or promote products derived from this software without specific prior
written permission.

NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE GRANTED BY THIS
LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESSFOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.util.Range;

/**
 * This file contains an example of an iterative (Non-Linear) "OpMode".
 * An OpMode is a 'program' that runs in either the autonomous or the teleop period of an FTC match.
 * The names of OpModes appear on the menu of the FTC Driver Station.
 * When an selection is made from the menu, the corresponding OpMode
 * class is instantiated on the Robot Controller and executed.
 * <p>
 * This particular OpMode just executes a basic Tank Drive Teleop for a PushBot
 * It includes all the skeletal structure that all iterative OpModes contain.
 * <p>
 * Use Android Studios to Copy this Class, and Paste it into your team's code folder with a new name.
 * Remove or comment out the @Disabled line to add this opmode to the Driver Station OpMode list
 */

@com.qualcomm.robotcore.eventloop.opmode.TeleOp(name = "This is abstract. Go away.", group = "Iterative Opmode")
// @Autonomous(...) is the other common choice
@Disabled

public abstract class TankOpMode extends OpMode {
    /* Declare OpMode members. */
    private ElapsedTime runtime = new ElapsedTime();
    protected DcMotor frontLeftMotor;
    protected DcMotor frontRightMotor;
    protected DcMotor backLeftMotor;
    protected DcMotor backRightMotor;
    protected boolean hasTwoMotors;

    public TankOpMode() {
        //placeholder so subclasses can call other constructors
    }


    protected TankOpMode(String lMotorName, String rMotorName) {

        frontLeftMotor = hardwareMap.dcMotor.get(lMotorName);
        frontRightMotor = hardwareMap.dcMotor.get(rMotorName);
        hasTwoMotors = true;

    }

    protected TankOpMode(String fLMotorName, String fRMotorName, String bLMotorName, String bRMotorName) {

        frontLeftMotor = hardwareMap.dcMotor.get(fLMotorName);
        frontRightMotor = hardwareMap.dcMotor.get(fRMotorName);
        backLeftMotor = hardwareMap.dcMotor.get(bLMotorName);
        backRightMotor = hardwareMap.dcMotor.get(bRMotorName);
        hasTwoMotors = false;
    }

    /*
     * Code to run ONCE when the driver hits INIT
     */
    @Override
    public void init() {
        telemetry.addData("Status", "Initialized");
        frontRightMotor.setDirection(DcMotor.Direction.REVERSE);
        if (!hasTwoMotors) {
            backRightMotor.setDirection(DcMotor.Direction.REVERSE);
        /* eg: Initialize the hardware variables. Note that the strings used here as parameters
         * to 'get' must correspond to the names assigned during the robot configuration
         * step (using the FTC Robot Controller app on the phone).
         */
            // leftMotor  = hardwareMap.dcMotor.get("left motor");
            // rightMotor = hardwareMap.dcMotor.get("right motor");

            // eg: Set the drive motor directions:
            // Reverse the motor that runs backwards when connected directly to the battery
            // leftMotor.setDirection(DcMotor.Direction.FORWARD); // Set to REVERSE if using AndyMark motors
            //  rightMotor.setDirection(DcMotor.Direction.REVERSE);// Set to FORWARD if using AndyMark motors
            // telemetry.addData("Status", "Initialized");
        }
    }

    /*
     * Code to run REPEATEDLY after the driver hits INIT, but before they hit PLAY
     */
    @Override
    public void init_loop() {
    }

    /*
     * Code to run ONCE when the driver hits PLAY
     */
    @Override
    public void start() {
        runtime.reset();
    }

    /*
     * Code to run REPEATEDLY after the driver hits PLAY but before they hit STOP
     */
    @Override
    public void loop () {
        telemetry.addData("Status", "Running: " + runtime.toString());
        CollectTelemetry();

        float left = gamepad1.left_stick_y;
        float right = gamepad1.right_stick_y;
        setMotors(left, right);
    }

    public void setMotors(float left, float right) {
        right = moderateMotorPower(Range.clip(right, -1f, 1f));
        left = moderateMotorPower(Range.clip(left, -1f, 1f));
        frontRightMotor.setPower(right);
        frontLeftMotor.setPower(left);
        if (!hasTwoMotors) {
            backRightMotor.setPower(right);
            backLeftMotor.setPower(left);

            // eg: Run wheels in tank mode (note: The joystick goes negative when pushed forwards)
            // leftMotor.setPower(-gamepad1.left_stick_y);
            // rightMotor.setPower(-gamepad1.right_stick_y);
        }
    }

    public float moderateMotorPower(float motorPower) {

        if (motorPower < 0.1 && motorPower > -0.1) {

            return 0;
        } else {
            //Negative adjusts for joysticks being negative when pushed forward
            return -motorPower;
        }
    }

    protected void CollectTelemetry() {
        telemetry.addData("G1LY", gamepad1.left_stick_y);
        telemetry.addData("G1RY", gamepad1.right_stick_y);
    }

    /*
     * Code to run ONCE after the driver hits STOP
     */
    @Override
    public void stop() {
        setMotors(0, 0);
    }

}
