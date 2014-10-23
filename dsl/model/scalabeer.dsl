module scalabeer 
{
  aggregate Beer {
    BeerInfo info;
    User*    adder; // addedBy
    
    detail<Grade.beer>  grades;
  }

  value BeerInfo {
    String name;
    String beerType;
  }

  snowflake<Beer> BeerGrid {
    info.name;
    info.beerType;
    adder.username addersName;
    adder;
    grades;

    calculated double averageGrade from 'it => (it.grades.Count() == 0) ? 0 : it.grades.Average(i => i.grade)'; // default?
  }

  aggregate User(username) {
    String username;
    String password; // Binary passwordHash;
    String email;

    detail<Grade.user> grades;
    detail<Beer.adder> beers;

    specification findUser 'it => it.username == username && it.password == password' {
      String username;
      String password;
    }
  }

  snowflake<User> UserGrid {
    username name;
    grades;
    beers;

    specification findUser 'it => it.name == name' {
      String name;
    }

    calculated double averageGivenGrade from 'it => it.grades.Select(g => g.grade).DefaultIfEmpty().Average()';
    calculated double averageBeerAddedGrade from 'it => it.beers.Where(beer => beer.grades.Count() > 0 ).Select(beer => beer.grades.Average(grade => grade.grade)).DefaultIfEmpty().Average()';
  }

  report UserReport {
    String username;
    UserGrid userGrid 'it => it.name == username';
    List<GradeGrid> userGrades 'it => it.user.username == username';
  }

  aggregate Grade(userID, beerID) {
    User *user;
    Beer *beer;

    String[] tags;
    String   detailedDescription;

    Int grade;
  }

  snowflake<Grade> GradeGrid {
    user;
    beer;
    tags;

    detailedDescription;
    grade;
  }
}
