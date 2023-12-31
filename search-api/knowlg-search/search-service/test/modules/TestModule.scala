package modules

import com.google.inject.AbstractModule
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import play.libs.akka.AkkaGuiceSupport
import utils.ActorNames

import scala.concurrent.{ExecutionContext, Future}

class TestModule extends AbstractModule with AkkaGuiceSupport{

    override def configure() = {
        super.configure()
        bindActor(classOf[TestActor], ActorNames.HEALTH_ACTOR)
        bindActor(classOf[TestActor], ActorNames.SEARCH_ACTOR)
        bindActor(classOf[TestActor], ActorNames.AUDIT_HISTORY_ACTOR)
        println("Initialized application actors for search-service")
    }
}

class TestActor extends BaseActor {

    implicit val ec: ExecutionContext = getContext().dispatcher

    override def onReceive(request: Request): Future[Response] = {
        Future(ResponseHandler.OK)
    }
}