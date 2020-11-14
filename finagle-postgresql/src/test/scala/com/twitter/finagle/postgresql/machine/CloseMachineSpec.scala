package com.twitter.finagle.postgresql.machine

import com.twitter.finagle.postgresql.BackendMessage.CloseComplete
import com.twitter.finagle.postgresql.BackendMessage.NoTx
import com.twitter.finagle.postgresql.BackendMessage.ReadyForQuery
import com.twitter.finagle.postgresql.FrontendMessage.Close
import com.twitter.finagle.postgresql.FrontendMessage.DescriptionTarget
import com.twitter.finagle.postgresql.FrontendMessage.Flush
import com.twitter.finagle.postgresql.PropertiesSpec
import com.twitter.finagle.postgresql.Response
import com.twitter.finagle.postgresql.Types.Name
import com.twitter.finagle.postgresql.machine.StateMachine.Complete
import com.twitter.finagle.postgresql.machine.StateMachine.NoOp
import com.twitter.finagle.postgresql.machine.StateMachine.Respond
import com.twitter.finagle.postgresql.machine.StateMachine.Send
import com.twitter.finagle.postgresql.machine.StateMachine.SendSeveral
import com.twitter.finagle.postgresql.machine.StateMachine.Transition
import com.twitter.util.Return

class CloseMachineSpec extends MachineSpec[Response.Ready.type] with PropertiesSpec {

  def checkStartup(name: Name, target: DescriptionTarget): StepSpec =
    checkResult("start is several messages") {
      case Transition(_, SendSeveral(msgs)) =>
        msgs.toList must beLike {
          case a :: b :: Nil =>
            a must beEqualTo(Send(Close(target, name)))
            b must beEqualTo(Send(Flush))
        }
    }

  def checkNoOp(name: String): StepSpec =
    checkResult(name) {
      case Transition(_, NoOp) => ok
    }

  def mkMachine(name: Name, target: DescriptionTarget): CloseMachine = new CloseMachine(name, target)

  def nominalSpec(name: Name, target: DescriptionTarget) =
    machineSpec(mkMachine(name, target))(
      checkStartup(name, target),
      receive(CloseComplete),
      checkResult("handles CloseComplete") {
        case Transition(_, Respond(Return(Response.Ready))) => ok
      },
      receive(ReadyForQuery(NoTx)),
      checkResult("handles ReadyForQuery") {
        case Complete(_, None) => ok
      }
    )

  "CloseMachine" should {
    "send multiple messages on start" in prop { (name: Name, target: DescriptionTarget) =>
      machineSpec(mkMachine(name, target)) {
        checkStartup(name, target)
      }
    }

    "handles closing" in prop { (name: Name, target: DescriptionTarget) =>
      nominalSpec(name, target)
    }
  }
}
