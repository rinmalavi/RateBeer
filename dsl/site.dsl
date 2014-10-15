module RateBeer {
    root Beer {
        BeerInfo info;

        User*   adder;

        detail grades from Grade.beer;
    }

    value BeerInfo {
        String  name;
        String  beerType;
    }

    snowflake BeerSnow from Beer {
        info.name;
        info.beerType;
        adder.username addersName;
        grades;
        adder;

        calculated double averageGrade from 'it => (it.grades.Count() == 0) ? 0 : it.grades.Average(i => i.grade)';
    }

    root User (username) {
        String username;
        String password;
        String email;

        detail grades   from Grade.user;

        detail beers    from Beer.adder;

        specification findUser 'it => it.username == username && it.password == password'
        {
            String username;
            String password;
        }
    }

    snowflake UserSnow from User {
        username name;
        grades;
        beers;

        specification findUser 'it => it.name == name'
        {
            String name;
        }

        calculated double averageGivenGrade from 'it => it.grades.Select(g => g.grade).DefaultIfEmpty().Average()';
        calculated double averageBeerAddedGrade from 'it => it.beers.Where(beer => beer.grades.Count() > 0 ).Select(beer => beer.grades.Average(grade => grade.grade)).DefaultIfEmpty().Average()';
    }

    root Grade(userID, beerID)  {
        User *user;
        Beer *beer;


        String[] tags;
        String detailedDescription;

        Int grade;
    }
}