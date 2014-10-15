package controllers

import RateBeer._
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
  private val locator = com.dslplatform.api.client.Bootstrap.init(getClass.getResourceAsStream("/project.props"))

  private lazy val userRepository = locator.resolve[PersistableRepository[User]]
  private lazy val userSnowRepository = locator.resolve[Repository[UserSnow]]
  private lazy val beerSnowRepository = locator.resolve[Repository[BeerSnow]]
  private lazy val beerRepository = locator.resolve[PersistableRepository[Beer]]
  private lazy val gradeRepository = locator.resolve[PersistableRepository[Grade]]
  private lazy implicit val ec = locator.resolve[ExecutionContext]
  private lazy implicit val duration = 10 minutes

  private def addUser(user: User): Future[String] = userRepository.insert(user)

  private def addBeer(beer: Beer) = beerRepository.insert(beer)

  private def getUser(username: String): Future[User] = userRepository.find(username)

  private def getUserInfo(username: String): Future[Seq[UserSnow]] = {
    userSnowRepository.search(UserSnow.findUser(username))
  }

  private def isValid(username: String, password: String): Future[Boolean] = {
    userRepository.search(new User.findUser(username, password)).map(_.nonEmpty)
  }

  def getBeers: Future[Seq[BeerSnow]] = beerSnowRepository.search()

  def getBeer(uri: String): Future[Beer] = beerRepository.find(uri)

  def getBeerInfo(uri: String): Future[BeerSnow] = beerSnowRepository.find(uri)

  def getUsers: Future[Seq[UserSnow]] = userSnowRepository.search()

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
      "email" -> text,
      "password" -> text
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
      "detailed" -> text,
      "tags" -> text
    )(Tuple3.apply)(Some.apply)
  }

  //-------------------------------------------------

  def index = {
    def indexPage(optUser: Option[UserSnow]) = Ok(view.html.index(optUser))

    Authenticated(username, rh => indexPage(None)) {
      username =>
        Action.async {
          getUserInfo(username).map(seq => indexPage(seq.headOption))
        }
    }
  }

  def userScreen(user2showName: String) = Action.async { implicit request =>
    def userScreenPage(currentUser: Option[UserSnow], user2show: Option[UserSnow]) = Ok(view.html.userscreen(currentUser, user2show))

    val user2showFO = getUserInfo(user2showName).map(_.headOption)

    val currentUserFO: Future[Option[UserSnow]] = request.session.get("username").map {
      currentUserName => getUserInfo(currentUserName).map(_.headOption)
    }.getOrElse(Future.successful(None))

    user2showFO.flatMap {
      user2show =>
        currentUserFO.map {
          currentUserO =>
            userScreenPage(currentUserO, user2show) // todo - report list of grades
        }
    }
  }

  def beerScreen(beer2showName: String) = Action.async {
    implicit request =>
      def beerScreenPage(currentUser: Option[UserSnow], beer2show: Option[BeerSnow]) = Ok(view.html.beerscreen(currentUser, beer2show))

      val beer2showFO = getBeerInfo(beer2showName)

      val currentUserFO: Future[Option[UserSnow]] = request.session.get("username").map {
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
    def usersPage(currentUser: Option[UserSnow], users: Seq[UserSnow]) = Ok(view.html.listusers(currentUser, users))

    val currentUserFO: Future[Option[UserSnow]] = request.session.get("username").map {
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

    def listBeersPage(currentUser: Option[UserSnow], beers: Seq[(BeerSnow, Option[Int])]) = Ok(view.html.listbeers(currentUser, beers))

    val currentUserFO: Future[Option[UserSnow]] = request.session.get("username").map {
      currentUserName => getUserInfo(currentUserName).map(_.headOption)
    }.getOrElse(Future.successful(None))

    getBeers.flatMap {
      beers =>
        currentUserFO.map {
          currentUserO =>
            val beersnow = beers.map{
              beer =>
                val userGrade = currentUserO.flatMap{
                  _.grades.filter{grade => grade.beer.info.name == beer.name }.map(_.grade).headOption // todo - hidden lazy load - optimize with report
                }
                (beer, userGrade)
            }
            listBeersPage(currentUserO, beersnow)
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
                    }.recover{ case e =>
                      println(e)
                      Results.NotModified}
                    inserted
                }
            }
          }
        )
      })
  }

  def newGrade(uri: String) = Action { implicit request =>
    Ok("Not there yet!")
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
                      Redirect(request.headers.get("Referer").getOrElse("/"))
                  }
                  inserted.onFailure {
                    case e => println(e)
                  }
                  inserted
              }
            }
          )
        }
      )
  }
}
