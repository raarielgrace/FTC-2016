package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;

import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder;
import org.firstinspires.ftc.teamcode.classes.DriveTo;
import org.firstinspires.ftc.teamcode.classes.DriveToComp;
import org.firstinspires.ftc.teamcode.classes.DriveToListener;
import org.firstinspires.ftc.teamcode.classes.DriveToParams;
import org.firstinspires.ftc.teamcode.classes.Gyro;
import org.firstinspires.ftc.teamcode.classes.MotorSide;
import org.firstinspires.ftc.teamcode.classes.TankDrive;
import org.firstinspires.ftc.teamcode.classes.TankMotor;
import org.firstinspires.ftc.teamcode.classes.VuforiaFTC;
import org.firstinspires.ftc.teamcode.classes.VuforiaTarget;

@com.qualcomm.robotcore.eventloop.opmode.TeleOp(name = "Vuforia Test", group = "Test")
public class VuforiaTest extends OpMode implements DriveToListener {

    public static final String TAG = "Vuforia Test";

    // Field, camera and robot constants
    private static final float MM_PER_INCH = 25.4f;
    private static final int BOT_WIDTH = (int) (18 * MM_PER_INCH);
    private static final int FIELD_WIDTH = (int) ((12 * 12 - 2) * MM_PER_INCH);
    private static final int GYRO_MIN_UPDATE_INTERVAL = 500;

    // Tracking config
    private static final String CONFIG_ASSET = "FTC_2016-17";
    private static final int CONFIG_TARGET_NUM = 4;
    // TODO: This location and rotation is imaginary, but should at least be close.
    private static final VuforiaTarget CONFIG_PHONE = new VuforiaTarget(
            "Phone",
            new float[]{BOT_WIDTH / 2, 0, 0},
            new float[]{-90, 0, 0},
            AxesOrder.YZY
    );
    // TODO: These locations are imaginary. We need to find the real ones before navigation.
    private static final float[] TARGETS_ROTATION_RED = {-90, 270, 0};
    private static final float[] TARGETS_ROTATION_BLUE = {90, 0, 0};
    private static final float[] TARGETS_OFFSET_RED = {250, 0, 0};
    private static final float[] TARGETS_OFFSET_BLUE = {0, -250, 0};
    private static final int TARGETS_Y_BLUE = FIELD_WIDTH / 2;
    private static final int TARGETS_X_RED = -FIELD_WIDTH / 2;
    private static final int TARGETS_OFFSET_NEAR = (int) (12 * MM_PER_INCH);
    private static final int TARGETS_OFFSET_FAR = (int) (36 * MM_PER_INCH);
    private static final VuforiaTarget[] CONFIG = {new VuforiaTarget(
            "Wheels",
            new float[]{TARGETS_OFFSET_NEAR, TARGETS_Y_BLUE, 0},
            TARGETS_OFFSET_BLUE,
            TARGETS_ROTATION_BLUE
    ), new VuforiaTarget(
            "Tools",
            new float[]{TARGETS_X_RED, TARGETS_OFFSET_FAR, 0},
            TARGETS_OFFSET_RED,
            TARGETS_ROTATION_RED
    ), new VuforiaTarget(
            "LEGO",
            new float[]{-TARGETS_OFFSET_FAR, TARGETS_Y_BLUE, 0},
            TARGETS_OFFSET_BLUE,
            TARGETS_ROTATION_BLUE
    ), new VuforiaTarget(
            "Gears",
            new float[]{TARGETS_X_RED, -TARGETS_OFFSET_NEAR, 0},
            TARGETS_OFFSET_RED,
            TARGETS_ROTATION_RED
    )};

    // Driving constants
    private static final float ENCODER_PER_MM = 3.2f;
    private static final int ENCODER_INDEX = 2;
    private static final float SPEED_TURN = 0.5f;
    private static final float SPEED_DRIVE = 1.0f;

    // Dynamic things we need to remember
    private VuforiaFTC vuforia;
    private TankDrive tank;
    private Gyro gyro;
    private DriveTo drive;
    private long lastHeadingSync;
    private int lastBearing = 0;
    private String thisTarget = "";
    private String lastTarget = "";

    // Sensor and reference types for our DriveTo callbacks
    enum SENSOR_TYPE {
        GYRO, GYRO_SECONDARY, ENCODER
    }

    @Override
    public void init() {

        // Placate drivers; sometimes VuforiaFTC is slow to init
        telemetry.addData(">", "Initializing...");
        telemetry.update();

        // Sensors
        gyro = new Gyro(hardwareMap, "gyro");
        if (!gyro.isAvailable()) {
            telemetry.log().add("ERROR: Unable to initalize gyro");
        }

        // Drive motors
        TankMotor motors[] = new TankMotor[4];
        motors[0] = new TankMotor("fl", MotorSide.LEFT);
        motors[1] = new TankMotor("fr", MotorSide.RIGHT, true);
        motors[2] = new TankMotor("bl", MotorSide.LEFT, true); // Encoder wheel
        motors[3] = new TankMotor("br", MotorSide.RIGHT);
        tank = new TankDrive(hardwareMap, motors);
        if (!tank.isAvailable()) {
            // Note that we could retry with different names to support multiple configs/robots
            telemetry.log().add("ERROR: Unable to initalize motors");
        }

        // Reduce speeds while testing
        tank.setSpeedScale(0.5);

        // Vuforia
        vuforia = new VuforiaFTC(CONFIG_ASSET, CONFIG_TARGET_NUM, CONFIG, CONFIG_PHONE);
        vuforia.init();

        // Wait for the game to begin
        telemetry.addData(">", "Ready for game start");
        telemetry.update();
    }

    @Override
    public void init_loop() {
    }

    @Override
    public void start() {
        telemetry.clearAll();

        // Start Vuforia tracking
        vuforia.start();

        // Allow driver control
        tank.stop();
        tank.setTeleop(true);
    }

    @Override
    public void loop() {
        // Avoid repeated system calls
        long now = System.currentTimeMillis();

        // Drive
        tank.loop(gamepad1);

        // Update our location and pose if we aren't auto-driving
        if (drive != null) {
            vuforia.track();
        }

        // Update the gyro offset if we have a fix
        if (!vuforia.isStale() && lastHeadingSync + GYRO_MIN_UPDATE_INTERVAL < now) {
            lastHeadingSync = now;
            gyro.setHeading(vuforia.getHeading());
        }

        // Driver feedback
        vuforia.display(telemetry);
        telemetry.addData("Encoder", tank.getEncoder(ENCODER_INDEX));
        if (!gyro.isReady()) {
            telemetry.addData("Gyro", "Calibrating (DO NOT DRIVE): %d" + (int) time);
        } else {
            telemetry.addData("Gyro Abs/Raw", gyro.getHeading() + "/" + gyro.getHeadingRaw());
        }

        // Handle DriveTo driving
        if (drive != null) {
            // DriveTo
            drive.drive();

            // Return to teleop when complete
            if (drive.isStarted() && drive.isDone()) {
                drive = null;
                tank.setTeleop(true);
            }
        }

        // Collect data about the first visible target
        boolean valid = false;
        int bearing = 0;
        int distance = 0;
        if (!vuforia.isStale()) {
            for (String target : vuforia.getVisible().keySet()) {
                if (vuforia.getVisible(target)) {
                    int index = vuforia.getTargetIndex(target);
                    bearing = vuforia.bearing(index);
                    distance = vuforia.distance(index);
                    valid = true;
                    thisTarget = target;
                    telemetry.addData("Bearing (" + thisTarget + ")", bearing);
                    break;
                }
            }
        }

        // Turn to face the first visible target
        if (drive == null && valid && gamepad1.y) {
            tank.setTeleop(false);
            int heading = gyro.getHeading();
            DriveToParams param1 = new DriveToParams(this, SENSOR_TYPE.GYRO);
            DriveToParams param2 = new DriveToParams(this, SENSOR_TYPE.GYRO_SECONDARY);

            // Turn the short way
            lastBearing = bearing;
            lastTarget = thisTarget;
            if ((bearing - heading + 360) % 360 <= 180) {
                // CW
                param1.greaterThan(bearing);
                param2.lessThan((bearing + 180) % 360);
            } else {
                // CCW
                param1.lessThan(bearing);
                param2.greaterThan((bearing + 180) % 360);
            }
            drive = new DriveTo(new DriveToParams[]{param1, param2});
        }

        // Drive half the distance to the first visible target
        if (drive == null && valid && gamepad1.x) {
            tank.setTeleop(false);
            DriveToParams param = new DriveToParams(this, SENSOR_TYPE.ENCODER);
            int ticks = (int) ((float) -distance * ENCODER_PER_MM);
            param.lessThan((ticks / 2) + tank.getEncoder(ENCODER_INDEX));
            drive = new DriveTo(new DriveToParams[]{param});
        }

        // Loop invariants
        telemetry.update();
    }

    @Override
    public void driveToStop(DriveToParams param) {
        tank.stop();
    }

    @Override
    public void driveToRun(DriveToParams param) {
        // Remember that "forward" is "negative" per the joystick conventions
        switch ((SENSOR_TYPE) param.reference) {
            case GYRO:
                // Turning clockwise increases heading
                if (param.comparator.equals(DriveToComp.GREATER)) {
                    telemetry.log().add("Target/Bearing: " + lastTarget + "/" + lastBearing);
                    telemetry.log().add("CW current/target: " + gyro.getHeading() + "/" + param.limit1);
                    tank.setSpeed(-SPEED_TURN, MotorSide.LEFT);
                    tank.setSpeed(SPEED_TURN, MotorSide.RIGHT);
                } else {
                    telemetry.log().add("Target/Bearing: " + lastTarget + "/" + lastBearing);
                    telemetry.log().add("CCW current/target: " + gyro.getHeading() + "/" + param.limit1);
                    tank.setSpeed(SPEED_TURN, MotorSide.LEFT);
                    tank.setSpeed(-SPEED_TURN, MotorSide.RIGHT);
                }
                break;
            case GYRO_SECONDARY:
                // Do nothing
                telemetry.log().add("Secondary " + param.comparator + " current/target: " + gyro.getHeading() + "/" + param.limit1);
                break;
            case ENCODER:
                // Always drive forward
                telemetry.log().add("Forward current/target: " + tank.getEncoder(ENCODER_INDEX) + "/" + param.limit1);
                tank.setSpeed(-SPEED_DRIVE);
                break;
        }
    }

    @Override
    public double driveToSensor(DriveToParams param) {
        double value = 0;
        switch ((SENSOR_TYPE) param.reference) {
            case GYRO:
                // Fallthrough
            case GYRO_SECONDARY:
                value = gyro.getHeading();
                break;
            case ENCODER:
                value = tank.getEncoder(ENCODER_INDEX);
                break;
        }
        return value;
    }
}
