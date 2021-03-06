package org.firstinspires.ftc.teamcode.auto;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;

import org.firstinspires.ftc.teamcode.field.Field;
import org.firstinspires.ftc.teamcode.driveto.DriveTo;
import org.firstinspires.ftc.teamcode.driveto.DriveToComp;
import org.firstinspires.ftc.teamcode.driveto.DriveToListener;
import org.firstinspires.ftc.teamcode.driveto.DriveToParams;
import org.firstinspires.ftc.teamcode.sensors.Gyro;
import org.firstinspires.ftc.teamcode.actuators.Motor;
import org.firstinspires.ftc.teamcode.wheels.MotorSide;
import org.firstinspires.ftc.teamcode.actuators.ServoFTC;
import org.firstinspires.ftc.teamcode.wheels.TankDrive;
import org.firstinspires.ftc.teamcode.vuforia.VuforiaFTC;
import org.firstinspires.ftc.teamcode.vuforia.VuforiaTarget;
import org.firstinspires.ftc.teamcode.config.MotorConfigs;
import org.firstinspires.ftc.teamcode.config.ServoConfigs;
import org.firstinspires.ftc.teamcode.config.VuforiaConfigs;
import org.firstinspires.ftc.teamcode.config.WheelMotorConfigs;

import java.util.NoSuchElementException;

@Disabled
@com.qualcomm.robotcore.eventloop.opmode.Autonomous(name = "Vuforia Auto", group = "AutoTest")
public class VuforiaAuto extends OpMode implements DriveToListener {

    // Driving constants
    private static final float GYRO_MIN_UPDATE_INTERVAL = 1.0f;
    private static final float ENCODER_PER_MM = 3.2f;
    private static final int ENCODER_INDEX = 2;
    private static final float SPEED_TURN = 0.1f;
    private static final float SPEED_TURN_FAST = 0.5f;
    private static final int SPEED_TURN_THRESHOLD = 60;
    private static final float SPEED_DRIVE = 1.0f;
    private static final int TIMEOUT_DEFAULT = DriveTo.TIMEOUT_DEFAULT;
    private static final int TIMEOUT_DEGREE = 100;
    private static final int OVERRUN_GYRO = 2;
    private static final int OVERRUN_ENCODER = 25;
    private static final float SPEED_SHOOT = 1.0f;

    // Autonomous routine constants
    private static final float GYRO_TIMEOUT = 5.0f;
    private static final int SHOOT_DISTANCE = 1850;
    private static final int SHOOT_SPIN = 3700;
    private static final int NUM_SHOTS = 2;
    private static final float SHOT_DELAY = 1.0f;
    private static final int BALL_DISTANCE = 1100;
    private static final int TURN_IN_ANGLE = 5;
    private static final int PAST_BALL_DISTANCE = 1000;
    private static final int BLIND_TURN = -40;
    private static final float FIND_TARGET_DELAY = 0.75f;
    private static final int FIND_TARGET_MAX = -BLIND_TURN + 60;
    private static final int FIND_TARGET_INCREMENT = FIND_TARGET_MAX / 5;
    private static final String FIRST_TARGET_BLUE = "LEGO";
    private static final String FIRST_TARGET_RED = "Tools";
    private static final int BLIND_BUMP = 1000;
    private static final int DESTINATION_OFFSET = (int) (12 * Field.MM_PER_INCH);
    private static final int APPROACH_MIN = 400;
    private static final float BEACON_DELAY = 1.0f;

    // Numeric constants
    private final static int FULL_CIRCLE = 360;

    // Devices and subsystems
    private final Field.AllianceColor color;
    private VuforiaTarget[] config;
    private VuforiaFTC vuforia;
    private TankDrive tank;
    private Gyro gyro;
    private DriveTo drive;
    private Motor shooter;
    private ServoFTC blocker;
    private ServoFTC booperLeft;
    private ServoFTC booperRight;

    // Dynamic things we need to remember
    private double headingSyncExpires = 0;
    private AUTO_STATE state = AUTO_STATE.INIT;
    private int shots = 0;
    private double timer = 0.0;
    private int target = -1;
    private Field.AllianceColor beacon = null;
    private int findTurnAccumulator = 0;
    private boolean waiting = false;
    private double autodriveComplete = 0.0;

    // Sensor reference types for our DriveTo callbacks
    enum SENSOR_TYPE {
        GYRO, DRIVE_ENCODER, SHOOT_ENCODER
    }

    // Enum for the main state machine
    enum AUTO_STATE {
        INIT,
        DRIVE_TO_SHOOT,
        SHOOT,
        SHOOT_WAIT,
        DRIVE_TO_BALL,
        TURN_IN,
        DRIVE_PAST_BALL,
        BLIND_TURN,
        FIND_TARGET,
        FIND_TARGET_WAIT,
        TURN_TO_DEST,
        DRIVE_TO_DEST,
        TURN_TO_TARGET,
        WAIT_TARGET,
        TARGET_NOT_VISIBLE,
        ALIGN_AT_TARGET,
        APPROACH_TARGET,
        ALIGN_TARGET_PLANE,
        BUMP_WALL,
        CHECK_COLOR,
        PRESS_BEACON,
        BEACON_WAIT,
        BACK_AWAY,
        DONE;

        // Private static copy to avoid repeated calls to values()
        private static final AUTO_STATE[] values = values();

        public AUTO_STATE prev() {
            int i = ordinal() - 1;
            if (i < 0) {
                throw new NoSuchElementException();
            }
            return values[i];
        }

        public AUTO_STATE next() {
            int i = ordinal() + 1;
            if (i >= values.length) {
                throw new NoSuchElementException();
            }
            return values[i];
        }

        public static final AUTO_STATE first = INIT;
        public static final AUTO_STATE last = DONE;
    }

    public VuforiaAuto(Field.AllianceColor color) {
        this.color = color;
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
        tank = new WheelMotorConfigs().init(hardwareMap, telemetry);
        tank.stop();

        // Shooter motor
        shooter = new MotorConfigs().init(hardwareMap, telemetry, "SHOOTER");
        shooter.stop();

        // Servos
        ServoConfigs servos = new ServoConfigs();
        blocker = servos.init(hardwareMap, telemetry, "BLOCKER");
        blocker.max(); // Max is down
        booperLeft = servos.init(hardwareMap, telemetry, "BOOPER-LEFT");
        booperLeft.min();
        booperRight = servos.init(hardwareMap, telemetry, "BOOPER-RIGHT");
        booperRight.min();

        // Vuforia
        config = VuforiaConfigs.Field();
        vuforia = new VuforiaFTC(VuforiaConfigs.AssetName, VuforiaConfigs.TargetCount,
                config, VuforiaConfigs.Bot());
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

        // Steady...
        state = AUTO_STATE.first;
        timer = time + GYRO_TIMEOUT;
    }

    @Override
    public void loop() {
        // Handle DriveTo driving
        if (drive != null) {
            // DriveTo
            drive.drive();

            // Return to teleop when complete
            if (drive.isDone()) {
                drive = null;
                tank.setTeleop(true);
                autodriveComplete = time;
            }
        }

        // Driver feedback
        telemetry.addData("State", state);
        vuforia.display(telemetry);
        telemetry.addData("Encoder", tank.getEncoder(ENCODER_INDEX));
        if (!gyro.isReady()) {
            telemetry.addData("Gyro", "Calibrating (DO NOT DRIVE): %d", (int) time);
        } else {
            telemetry.addData("Gyro Abs/Rel", gyro.getHeading() + "°/" + gyro.getHeadingRaw() + "°");
        }
        telemetry.update();

        /*
         * Cut the loop short when we are auto-driving
         * This keeps us out of the state machine until the last auto-drive command is complete
         */
        if (drive != null) {
            return;
        }

        // Update our location and target info
        vuforia.track();

        // Update the gyro offset if we have a fix
        if (!vuforia.isStale() && headingSyncExpires < time) {
            headingSyncExpires = time + GYRO_MIN_UPDATE_INTERVAL;
            gyro.setHeading(vuforia.getHeading());
        }

        // Main state machine
        int angle = 0;
        int bearing;
        DriveToParams param;
        switch (state) {
            case INIT:
                if (gyro.isReady()) {
                    state = AUTO_STATE.DRIVE_TO_SHOOT;
                } else if (timer < time) {
                    gyro.disable();
                    state = state.next();
                }
                break;
            case DRIVE_TO_SHOOT:
                driveForward(SHOOT_DISTANCE);
                state = state.next();
                break;
            case SHOOT:
                blocker.min(); // Min is up
                param = new DriveToParams(this, SENSOR_TYPE.SHOOT_ENCODER);
                param.greaterThan(SHOOT_SPIN);
                drive = new DriveTo(new DriveToParams[]{param});
                timer = time + SHOT_DELAY;
                shots++;
                state = state.next();
                break;
            case SHOOT_WAIT:
                blocker.max(); // Max is down
                if (timer < time) {
                    if (shots >= NUM_SHOTS) {
                        state = state.next();
                    } else {
                        state = state.prev();
                    }
                }
                break;
            case DRIVE_TO_BALL:
                driveForward(BALL_DISTANCE);
                state = state.next();
                break;
            case TURN_IN:
                angle = TURN_IN_ANGLE;
                if (Field.AllianceColor.RED.equals(color)) {
                    angle *= -1;
                }
                turnAngle(angle);
                state = state.next();
                break;
            case DRIVE_PAST_BALL:
                driveForward(PAST_BALL_DISTANCE);
                state = state.next();
                break;
            case BLIND_TURN:
                if (!gyro.isReady()) {
                    // Bail if we have no gyro
                    state = AUTO_STATE.last;
                } else {
                    // Turn away first; we might see the nearby alternate alliance target
                    angle = BLIND_TURN;
                    if (Field.AllianceColor.RED.equals(color)) {
                        angle *= -1;
                    }
                    turnAngle(angle);
                    waiting = false;
                    state = AUTO_STATE.FIND_TARGET_WAIT;
                }
                // Reset the accumulator for use in FIND_TARGET
                findTurnAccumulator = 0;
                break;
            case FIND_TARGET:
                if (findTurnAccumulator < FIND_TARGET_MAX) {
                    turnAngle(FIND_TARGET_INCREMENT);
                    findTurnAccumulator += FIND_TARGET_INCREMENT;
                    // Reset the waiting flag for FIND_TARGET_WAIT
                    waiting = false;
                    state = state.next();
                } else {
                    state = AUTO_STATE.last;
                }
                break;
            case FIND_TARGET_WAIT:
                if (!waiting) {
                    timer = autodriveComplete + FIND_TARGET_DELAY;
                    waiting = true;
                }
                if (!vuforia.isStale()) {
                    // Select a target when we have a vision fix
                    target = firstTarget(color);
                    telemetry.log().add("Selected target " + config[target].name);

                    // Sync the gyro before turning
                    gyro.setHeading(vuforia.getHeading());
                    state = state.next();
                } else if (timer < time) {
                    // Turn more if we still can't see a target
                    state = state.prev();
                }
                break;
            case TURN_TO_DEST:
                if (target < 0) {
                    // Bail if we have no target
                    telemetry.log().add("No target found. Aborting...");
                    state = AUTO_STATE.last;
                    break;
                }
                bearing = vuforia.bearing(destinationXY(target));
                turnBearing(bearing);
                telemetry.log().add("Turning to " + config[target].name + "-dest @ " + bearing + "°");
                state = state.next();
                break;
            case DRIVE_TO_DEST:
                if (target >= 0) {
                    int distance = vuforia.distance(destinationXY(target));
                    driveForward(distance);
                    telemetry.log().add("Driving to " + config[target].name + "-dest @ " + distance + "mm");
                }
                state = state.next();
                break;
            case TURN_TO_TARGET:
                if (Field.AllianceColor.BLUE.equals(color)) {
                    bearing = 90;
                } else {
                    bearing = 0;
                }
                turnBearing(bearing);
                telemetry.log().add("Turning to " + config[target].name + " wall @ " + bearing + "°");
                // Reset the waiting flag for WAIT_TARGET
                waiting = false;
                // Reset the accumulator for use in TARGET_NOT_VISIBLE
                findTurnAccumulator = 0;
                state = state.next();
                break;
            case WAIT_TARGET:
                if (!waiting) {
                    timer = autodriveComplete + FIND_TARGET_DELAY;
                    waiting = true;
                }
                if (!vuforia.isStale()) {
                    gyro.setHeading(vuforia.getHeading());
                    state = state.ALIGN_AT_TARGET;
                } else if (timer < time) {
                    telemetry.log().add("Target yet not visible for approach. Searching...");
                    state = state.next();
                }
                break;
            case TARGET_NOT_VISIBLE:
                // Turn cw by on attempt 1, ccw on attempt 2, then give up
                if (findTurnAccumulator == 0) {
                    telemetry.log().add("Searching CW for target");
                    angle = FIND_TARGET_INCREMENT;
                } else if (findTurnAccumulator * FIND_TARGET_INCREMENT > 0) {
                    telemetry.log().add("Searching CCW for target");
                    angle = FIND_TARGET_INCREMENT * -2;
                } else {
                    telemetry.log().add("Unable to reacquire target. Aborting...");
                    state = AUTO_STATE.last;
                    break;
                }
                findTurnAccumulator = angle;
                turnAngle(angle);
                // Reset the waiting flag for WAIT_TARGET
                waiting = false;
                state = state.prev();
                break;
            case ALIGN_AT_TARGET:
                if (vuforia.isStale()) {
                    telemetry.log().add("Unable to align at target. Aborting...");
                    state = AUTO_STATE.last;
                    break;
                } else {
                    bearing = vuforia.bearing(target);
                    turnBearing(bearing);
                    telemetry.log().add("Turning to " + config[target].name + " @ " + bearing + "°");
                    state = state.next();
                }
                break;
            case APPROACH_TARGET:
                if (vuforia.isStale()) {
                    telemetry.log().add("Unable to locate target for approach. Attempting blind bump.");
                    driveForward(BLIND_BUMP);
                    state = AUTO_STATE.CHECK_COLOR;
                    break;
                }
                int distance = vuforia.distance(target);
                if (distance < APPROACH_MIN) {
                    state = state.next();
                } else {
                    telemetry.log().add("Driving toward " + config[target].name + " @ " + distance + "mm");
                    // Drive half the distance to allow multiple alignments
                    driveForward(distance / 2);
                    state = state.prev();
                }
                break;
            case ALIGN_TARGET_PLANE:
                if (vuforia.getVisible(config[target].name)) {
                    turnAngle(vuforia.getTargetAngle(config[target].name));
                    state = state.next();
                } else {
                    telemetry.log().add("Unable to align to target plane");
                    state = AUTO_STATE.last;
                }
                // TODO: Stop for debug
                state = AUTO_STATE.last;
                break;
            case BUMP_WALL:
                driveForward(APPROACH_MIN / 2);
                state = state.next();
                break;
            case CHECK_COLOR:
                // TODO: Check the color sensor for beacon configuration
                beacon = Field.AllianceColor.BLUE;
                state = state.next();
                break;
            case PRESS_BEACON:
                // TODO: This depends on where the color sensor is mounted
                if (Field.AllianceColor.RED.equals(beacon)) {
                    booperLeft.max();
                } else {
                    booperRight.max();
                }
                timer = time + BEACON_DELAY;
                state = state.next();
                break;
            case BEACON_WAIT:
                if (timer < time) {
                    state = state.next();
                }
                break;
            case BACK_AWAY:
                booperLeft.min();
                booperRight.min();
                driveForward(-DESTINATION_OFFSET);
                state = state.next();
                break;
            case DONE:
                // Nothing
                break;
        }
    }

    @Override
    public void driveToStop(DriveToParams param) {
        switch ((SENSOR_TYPE) param.reference) {
            case GYRO:
                // Fall through
            case DRIVE_ENCODER:
                tank.stop();
                break;
            case SHOOT_ENCODER:
                shooter.setPower(0);
                break;
        }
    }

    @Override
    public void driveToRun(DriveToParams param) {
        // Remember that "forward" is "negative" per the joystick conventions
        switch ((SENSOR_TYPE) param.reference) {
            case GYRO:
                // Turn faster if we're outside the threshold
                float speed = SPEED_TURN;
                if (Math.abs(param.error1) > SPEED_TURN_THRESHOLD) {
                    speed = SPEED_TURN_FAST;
                }

                // Turning clockwise increases heading
                if (param.comparator.equals(DriveToComp.GREATER)) {
                    tank.setSpeed(-speed, MotorSide.LEFT);
                    tank.setSpeed(speed, MotorSide.RIGHT);
                } else {
                    tank.setSpeed(speed, MotorSide.LEFT);
                    tank.setSpeed(-speed, MotorSide.RIGHT);
                }
                break;
            case DRIVE_ENCODER:
                tank.setSpeed(-SPEED_DRIVE);
                break;
            case SHOOT_ENCODER:
                shooter.setPower(SPEED_SHOOT);
                break;
        }
    }

    @Override
    public double driveToSensor(DriveToParams param) {
        double value = 0;
        switch ((SENSOR_TYPE) param.reference) {
            case GYRO:
                value = gyro.getHeading();
                break;
            case DRIVE_ENCODER:
                value = tank.getEncoder(ENCODER_INDEX);
                break;
            case SHOOT_ENCODER:
                value = shooter.getEncoder();
                break;
        }
        return value;
    }

    private void turnAngle(int angle) {
        tank.setTeleop(false);
        DriveToParams param = new DriveToParams(this, SENSOR_TYPE.GYRO);
        param.timeout = (Math.abs(angle) * TIMEOUT_DEGREE) + TIMEOUT_DEFAULT;

        // Normalized heading and bearing
        int target = gyro.getHeading() + angle;

        // Turn CCW for negative angles
        if (angle > 0) {
            param.greaterThan(target - OVERRUN_GYRO);
        } else {
            param.lessThan(target + OVERRUN_GYRO);
        }
        drive = new DriveTo(new DriveToParams[]{param});
    }

    private void turnBearing(int bearing) {
        // Normalized heading and turns in each direction
        int heading = gyro.getHeadingBasic();
        int cw = (bearing - heading + FULL_CIRCLE) % FULL_CIRCLE;
        int ccw = (heading - bearing + FULL_CIRCLE) % FULL_CIRCLE;

        // Turn the short way
        if (Math.abs(cw) <= Math.abs(ccw)) {
            turnAngle(cw);
        } else {
            turnAngle(-ccw);
        }
    }

    private void driveForward(int distance) {
        tank.setTeleop(false);
        DriveToParams param = new DriveToParams(this, SENSOR_TYPE.DRIVE_ENCODER);
        int ticks = (int) ((float) -distance * ENCODER_PER_MM);
        param.lessThan(ticks + tank.getEncoder(ENCODER_INDEX) - OVERRUN_ENCODER);
        drive = new DriveTo(new DriveToParams[]{param});
    }

    private int firstTarget(Field.AllianceColor color) {
        int index = -1;
        for (int i = 0; i < config.length; i++) {
            if (config[i].color.equals(color)) {
                // TODO: An ENUM-based array would be better, but not tonight
                if (config[i].name.equals(FIRST_TARGET_BLUE) || config[i].name.equals(FIRST_TARGET_RED)) {
                    index = i;
                    break;
                }
            }
        }
        return index;
    }

    private int closestTarget(Field.AllianceColor color) {
        int index = -1;
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < config.length; i++) {
            if (config[i].color.equals(color)) {
                int distance = vuforia.distance(config[i].adjusted[0], config[i].adjusted[1]);
                if (distance < min) {
                    min = distance;
                    index = i;
                }
            }
        }
        return index;
    }

    private int[] destinationXY(int index) {
        int[] destination = new int[]{config[index].adjusted[0], config[index].adjusted[1]};
        // TODO: This is imaginary and must be calibrated before use
        // Pick a spot in front of the destination target to allow better alignment
        if (config[index].color.equals(Field.AllianceColor.BLUE)) {
            destination[0] -= DESTINATION_OFFSET;
        } else {
            destination[1] -= DESTINATION_OFFSET;
        }
        return destination;
    }
}