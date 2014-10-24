package controllers

import com.dslplatform.api.client.ReportingProxy
import com.scalabeer._
import com.dslplatform.api.patterns._
import org.slf4j.LoggerFactory

import play.api._
import play.api.mvc._
import play.api.mvc.Security._
import play.api.data.Form
import play.api.data.Forms._

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._

object Application extends Controller {

  private val logger = LoggerFactory.getLogger(getClass)

  // -----------------------------------------
  private val locator = com.dslplatform.api.client.Bootstrap.init(getClass.getResourceAsStream("/dsl-project.props"))

  private lazy val userRepository = locator.resolve[PersistableRepository[User]]
  private lazy val UserGridRepository = locator.resolve[Repository[UserGrid]]
  private lazy val beerGridRepository = locator.resolve[Repository[BeerGrid]]
  private lazy val beerRepository = locator.resolve[PersistableRepository[Beer]]
  private lazy val gradeRepository = locator.resolve[PersistableRepository[Grade]]
  private lazy val reportingProxy = locator.resolve[ReportingProxy]

  private lazy implicit val ec = locator.resolve[ExecutionContext]
  private lazy implicit val duration = 10 minutes

  private def addUser(user: User): Future[String] = userRepository.insert(user)

  private def addBeer(beer: Beer) = beerRepository.insert(beer)

  private def getUser(username: String): Future[User] = userRepository.find(username)

  private def getUserInfo(username: String): Future[Seq[UserGrid]] = {
    UserGridRepository.search(UserGrid.findUser(username))
  }

  private def getUserReport(username: String): Future[UserReport.Result] = {
    reportingProxy.populate(new UserReport(username))
  }

  private def isValid(username: String, password: String): Future[Boolean] = {
    userRepository.search(new User.findUser(username, password)).map(_.nonEmpty)
  }

  def getBeers: Future[Seq[BeerGrid]] = beerGridRepository.search()

  def getBeer(uri: String): Future[Beer] = beerRepository.find(uri)

  def getBeerInfo(uri: String): Future[BeerGrid] = beerGridRepository.find(uri)

  def getUsers: Future[Seq[UserGrid]] = UserGridRepository.search()

  def addGrade(grade: Grade) = gradeRepository.insert(grade)

  //-----------  Security -------------------------

  def username(request: RequestHeader) = {
    request.session.get("username")
    //optHash.flatMap{hash => getUser(hash)}
  }

  //-----------  Forms   ---------------------------
  // todo - make real mapping

  val signInForm: Form[(String, String)] = Form {
    mapping(
      "name" -> text,
      "password" -> text
    )(Tuple2.apply)(Some.apply)
  }

  val signUpForm: Form[(String, String, String)] = Form {
    mapping(
      "name" -> text,
      "password" -> text,
      "email" -> text
    )(Tuple3.apply)(Some.apply)
  }

  val beerForm: Form[(String, String)] = Form {
    mapping(
      "name" -> text,
      "type" -> text
    )(Tuple2.apply)(Some.apply)
  }

  val simpleGradeForm: Form[String] = Form {
    mapping(
      "grade" -> text
    )(x => x)(Some.apply)
  }

  val gradeForm: Form[(String, String, String)] = Form {
    mapping(
      "grade" -> text,
      "description" -> text,
      "tags" -> text
    )(Tuple3.apply)(Some.apply)
  }

  //-------------------------------------------------

  def index = {
    def indexPage(optUser: Option[UserGrid]) = Ok(view.html.index(optUser))

    Authenticated(username, rh => indexPage(None)) {
      username =>
        Action.async {
          getUserInfo(username).map(seq => indexPage(seq.headOption))
        }
    }
  }

  def userScreen(user2showName: String) = Action.async { implicit request =>
    def userScreenPage(currentUser: Option[UserGrid], user2show: UserReport.Result) = Ok(view.html.userscreen(currentUser, user2show))

    val user2showFO = getUserReport(user2showName)

    val currentUserFO: Future[Option[UserGrid]] = request.session.get("username").map {
      currentUserName => getUserInfo(currentUserName).map(_.headOption)
    }.getOrElse(Future.successful(None))

    user2showFO.flatMap {
      user2show =>
        currentUserFO.map {
          currentUserO =>
            userScreenPage(currentUserO, user2show)
        }
    }
  }

  def beerScreen(beer2showName: String) = Action.async {
    implicit request =>
      def beerScreenPage(currentUser: Option[UserGrid], beer2show: Option[BeerGrid]) = Ok(view.html.beerscreen(currentUser, beer2show))

      val beer2showFO = getBeerInfo(beer2showName)

      val currentUserFO: Future[Option[UserGrid]] = request.session.get("username").map {
        currentUserName => getUserInfo(currentUserName).map(_.headOption)
      }.getOrElse(Future.successful(None))

      beer2showFO.flatMap {
        beer2show =>
          currentUserFO.map {
            currentUserO =>
              beerScreenPage(currentUserO, Option(beer2show))
          }
      }
  }

  def users = Action.async { implicit request =>
    def usersPage(currentUser: Option[UserGrid], users: Seq[UserGrid]) = Ok(view.html.listusers(currentUser, users))

    val currentUserFO: Future[Option[UserGrid]] = request.session.get("username").map {
      currentUserName => getUserInfo(currentUserName).map(_.headOption)
    }.getOrElse(Future.successful(None))

    getUsers.flatMap {
      users =>
        currentUserFO.map {
          currentUserO =>
            usersPage(currentUserO, users)
        }
    }
  }

  def beers = Action.async { implicit request => // todo - make report

    def listBeersPage(currentUser: Option[UserGrid], beers: Seq[(BeerGrid, Option[Int])]) = Ok(view.html.listbeers(currentUser, beers))

    val currentUserFO: Future[Option[UserGrid]] = request.session.get("username").map {
      currentUserName => getUserInfo(currentUserName).map(_.headOption)
    }.getOrElse(Future.successful(None))

    getBeers.flatMap {
      beers =>
        currentUserFO.map {
          currentUserO =>
            val BeerGrid = beers.map {
              beer =>
                val userGrade = currentUserO.flatMap {
                  _.grades.filter { grade => grade.beer.info.name == beer.name}.map(_.grade).headOption // todo - hidden lazy load - optimize with report
                }
                (beer, userGrade)
            }
            listBeersPage(currentUserO, BeerGrid)
        }
    }
  }

  def logout = Action {
    implicit request =>
      Redirect(request.headers.get("Referer").getOrElse("/")).removingFromSession("username")
  }

  def login = Action.async { implicit request =>
    signInForm.bindFromRequest.fold(
      _ => Future.successful(Redirect(request.headers.get("Referer").getOrElse("/"))),
      o => {
        isValid(o._1, o._2).map {
          valid =>
            if (valid)
              Redirect(request.headers.get("Referer").getOrElse("/")).withSession(("username", o._1))
            else
              Redirect(request.headers.get("Referer").getOrElse("/"))
        }
      })
  }

  def signUp() = Action.async { implicit request =>
    signUpForm.bindFromRequest.fold(
      _ => Future.successful(Redirect(request.headers.get("Referer").getOrElse("/"))),
      o => {
        val newuser = User(o._1, o._2, o._3)
        addUser(newuser).map {
          _ =>
            Redirect(request.headers.get("Referer").getOrElse("/")).withSession(("username", o._1))
        }
      })
  }

  def newSimpleGrade(uri: String) = Action.async { implicit request =>
    request.session.get("username").fold(
      Future.successful(BadRequest("Not signed in!")))(
        username => {
          simpleGradeForm.bindFromRequest().fold(
            _ => Future.successful(BadRequest("Not a grade!")),
            gradeStr => {
              getUser(username).flatMap {
                user =>
                  getBeer(uri).flatMap {
                    beer =>
                      val grade = Grade(user, beer, grade = gradeStr.toInt)
                      val inserted = gradeRepository.insert(grade).map {
                        _ =>
                          Ok("You graded " + gradeStr)
                      }.recover { case e =>
                        println(e)
                        Results.NotModified
                      }
                      inserted
                  }
              }
            }
          )
        })
  }

  def newGrade(uri: String) = Action.async { implicit request =>
    request.session.get("username").fold(
      Future.successful(BadRequest("Not signed in!")))(
        username => {
          gradeForm.bindFromRequest().fold(
          a => Future.successful(BadRequest("Not a grade!" + a)), {
            case (g: String, d: String, t: String) =>
              getUser(username).flatMap {
                user =>
                  getBeer(uri).flatMap {
                    beer =>
                      val grade = Grade(user, beer, grade = g.toInt, detailedDescription = d, tags = t.split(" "))
                      val inserted = gradeRepository.insert(grade).map {
                        _ =>
                          Ok("You graded " + g)
                      }.recover { case e =>
                        println(e)
                        Results.NotModified
                      }
                      inserted
                  }
              }
          })
        }
      )
  }

  //-------------------------------------------------

  def newBeer() = Action.async { implicit request =>
    request.session.get("username").fold(
      Future.successful(BadRequest("Not signed in!")))(
        username => {
          beerForm.bindFromRequest().fold(
            _ => Future.successful(BadRequest("Not a beer!")),
            o => {
              getUser(username).flatMap {
                user =>
                  val beerInfo = BeerInfo(o._1, o._2)
                  val newBeer = Beer(beerInfo, user)
                  val inserted = beerRepository.insert(newBeer).map {
                    _ =>
                      Ok("Successfully added new beer")
                  }
                  inserted.recover {
                    case e =>
                      println(e)
                      Results.NotModified
                  }
                  inserted
              }
            }
          )
        }
      )
  }
}
