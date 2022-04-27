package com.linagora.gatling.imap.protocol.command

import java.util.function.Consumer

import akka.actor.{ActorRef, Props}
import com.linagora.gatling.imap.protocol._
import com.yahoo.imapnio.async.client.ImapAsyncSession
import com.yahoo.imapnio.async.request.{NamespaceCommand, NoopCommand}
import com.yahoo.imapnio.async.response.ImapAsyncResponse
import io.gatling.core.akka.BaseActor

import scala.collection.immutable.Seq

object NamespaceHandler {
  def props(session: ImapAsyncSession) = Props(new NamespaceHandler(session))
}

class NamespaceHandler(session: ImapAsyncSession) extends BaseActor {
  override def receive: Receive = {
    case Command.Namespace(userId) =>
      logger.trace(s"NamespaceHandler for user : ${userId.value}, on actor ${self.path} responding to ${sender.path}")
      context.become(waitForLoggedIn(sender()))

      val responseCallback: Consumer[ImapAsyncResponse] = responses => {
        import collection.JavaConverters._

        val responsesList = ImapResponses(responses.getResponseLines.asScala.to[Seq])
        logger.trace(s"On response for $userId :\n ${responsesList.mkString("\n")}")
        self !  Response.NamespaceResponse(responsesList)
      }

      val errorCallback: Consumer[Exception] = e => {
        logger.trace(s"${getClass.getSimpleName} command failed", e)
        logger.error(s"${getClass.getSimpleName} command failed")
        sender ! e
        context.stop(self)
      }

      val future = session.execute(new NamespaceCommand())
      future.setDoneCallback(responseCallback)
      future.setExceptionCallback(errorCallback)
  }

  def waitForLoggedIn(sender: ActorRef): Receive = {
    case msg@Response.NamespaceResponse(_) =>
      logger.trace(s"NamespaceHandler respond to ${sender.path} with $msg")
      sender ! msg
      context.stop(self)
  }
}
