import enumerate.Action;
import enumerate.State;
import gameInterface.AIInterface;

import java.util.LinkedList;
import java.util.Vector;

import simulator.Simulator;
import structs.CharacterData;
import structs.FrameData;
import structs.GameData;
import structs.Key;
import structs.MotionData;

import commandcenter.CommandCenter;

/**
 * MCTS(モンテカルロ木探索)により実装するAI
 *
 * @author Taichi
 */
public class Ranezi implements AIInterface {

  private Simulator simulator;
  private Key key;
  private CommandCenter commandCenter;
  private boolean playerNumber;
  private GameData gameData;

  /** 大本のFrameData */
  private FrameData frameData;

  /** 大本よりFRAME_AHEAD分遅れたFrameData */
  private FrameData simulatorAheadFrameData;

  /** 自分が行える行動全て */
  private LinkedList<Action> myActions;

  /** 相手が行える行動全て */
  private LinkedList<Action> oppActions;

  /** 自分の情報 */
  private CharacterData myCharacter;

  /** 相手の情報 */
  private CharacterData oppCharacter;

  /** フレームの調整用時間(JerryMizunoAIを参考) */
  private static final int FRAME_AHEAD = 14;

  private Vector<MotionData> myMotion;

  private Vector<MotionData> oppMotion;

  private Action[] actionAir;

  private Action[] actionGround;

  private Action spSkill;

  private Node rootNode;

  /** デバッグモードであるかどうか。trueの場合、様々なログが出力される */
  public static final boolean DEBUG_MODE = false;

  @Override
  public void close() {}

  @Override
  public String getCharacter() {
    return CHARACTER_ZEN;
  }

  @Override
  public void getInformation(FrameData frameData) {
    this.frameData = frameData;
    this.commandCenter.setFrameData(this.frameData, playerNumber);

    if (playerNumber) {
      myCharacter = frameData.getP1();
      oppCharacter = frameData.getP2();
    } else {
      myCharacter = frameData.getP2();
      oppCharacter = frameData.getP1();
    }
  }

  @Override
  public int initialize(GameData gameData, boolean playerNumber) {
    this.playerNumber = playerNumber;
    this.gameData = gameData;

    this.key = new Key();
    this.frameData = new FrameData();
    this.commandCenter = new CommandCenter();

    this.myActions = new LinkedList<Action>();
    this.oppActions = new LinkedList<Action>();

    simulator = gameData.getSimulator();

    actionAir =
        new Action[] {Action.AIR_GUARD, Action.AIR_A, Action.AIR_B, Action.AIR_DA, Action.AIR_DB,
            Action.AIR_FA, Action.AIR_FB, Action.AIR_UA, Action.AIR_UB, Action.AIR_D_DF_FA,
            Action.AIR_D_DF_FB, Action.AIR_F_D_DFA, Action.AIR_F_D_DFB, Action.AIR_D_DB_BA,
            Action.AIR_D_DB_BB};
    actionGround =
        new Action[] {Action.STAND_D_DB_BA, Action.BACK_STEP, Action.FORWARD_WALK, Action.DASH,
            Action.JUMP, Action.FOR_JUMP, Action.BACK_JUMP, Action.STAND_GUARD,
            Action.CROUCH_GUARD, Action.THROW_A, Action.THROW_B, Action.STAND_A, Action.STAND_B,
            Action.CROUCH_A, Action.CROUCH_B, Action.STAND_FA, Action.STAND_FB, Action.CROUCH_FA,
            Action.CROUCH_FB, Action.STAND_D_DF_FA, Action.STAND_D_DF_FB, Action.STAND_F_D_DFA,
            Action.STAND_F_D_DFB, Action.STAND_D_DB_BB};
    spSkill = Action.STAND_D_DF_FC;

    myMotion = this.playerNumber ? gameData.getPlayerOneMotion() : gameData.getPlayerTwoMotion();
    oppMotion = this.playerNumber ? gameData.getPlayerTwoMotion() : gameData.getPlayerOneMotion();

    return 0;
  }

  @Override
  public Key input() {
    return key;
  }

  @Override
  public void processing() {

    if (canProcessing()) {
      if (commandCenter.getskillFlag()) {
        key = commandCenter.getSkillKey();
      } else {
        key.empty();
        commandCenter.skillCancel();

        mctsPrepare(); // MCTSの下準備を行う
        rootNode =
            new Node(simulatorAheadFrameData, null, myActions, oppActions, gameData, playerNumber,
                commandCenter);
        rootNode.createNode();

        Action bestAction = rootNode.mcts(); // MCTSの実行
        if (Ranezi.DEBUG_MODE) {
          rootNode.printNode(rootNode);
        }

        commandCenter.commandCall(bestAction.name()); // MCTSで選択された行動を実行する
      }
    }
  }

  /**
   * AIが行動できるかどうかを判別する
   *
   * @return AIが行動できるかどうか
   */
  public boolean canProcessing() {
    return !frameData.getEmptyFlag() && frameData.getRemainingTime() > 0;
  }

  /**
   * MCTSの下準備 <br>
   * 14フレーム進ませたFrameDataの取得などを行う
   */
  public void mctsPrepare() {
    simulatorAheadFrameData = simulator.simulate(frameData, playerNumber, null, null, FRAME_AHEAD);

    myCharacter = playerNumber ? simulatorAheadFrameData.getP1() : simulatorAheadFrameData.getP2();
    oppCharacter = playerNumber ? simulatorAheadFrameData.getP2() : simulatorAheadFrameData.getP1();

    setMyAction();
    setOppAction();
  }

  public void setMyAction() {
    myActions.clear();

    int energy = myCharacter.getEnergy();

    if (myCharacter.getState() == State.AIR) {
      for (int i = 0; i < actionAir.length; i++) {
        if (Math.abs(myMotion.elementAt(Action.valueOf(actionAir[i].name()).ordinal())
            .getAttackStartAddEnergy()) <= energy) {
          myActions.add(actionAir[i]);
        }
      }
    } else {
      if (Math.abs(myMotion.elementAt(Action.valueOf(spSkill.name()).ordinal())
          .getAttackStartAddEnergy()) <= energy) {
        myActions.add(spSkill);
      }

      for (int i = 0; i < actionGround.length; i++) {
        if (Math.abs(myMotion.elementAt(Action.valueOf(actionGround[i].name()).ordinal())
            .getAttackStartAddEnergy()) <= energy) {
          myActions.add(actionGround[i]);
        }
      }
    }

  }

  public void setOppAction() {
    oppActions.clear();

    int energy = oppCharacter.getEnergy();

    if (oppCharacter.getState() == State.AIR) {
      for (int i = 0; i < actionAir.length; i++) {
        if (Math.abs(oppMotion.elementAt(Action.valueOf(actionAir[i].name()).ordinal())
            .getAttackStartAddEnergy()) <= energy) {
          oppActions.add(actionAir[i]);
        }
      }
    } else {
      if (Math.abs(oppMotion.elementAt(Action.valueOf(spSkill.name()).ordinal())
          .getAttackStartAddEnergy()) <= energy) {
        oppActions.add(spSkill);
      }

      for (int i = 0; i < actionGround.length; i++) {
        if (Math.abs(oppMotion.elementAt(Action.valueOf(actionGround[i].name()).ordinal())
            .getAttackStartAddEnergy()) <= energy) {
          oppActions.add(actionGround[i]);
        }
      }
    }
  }
}
