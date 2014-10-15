# There Are Things 2BD!

### Add An Enum
### Missing Play Part
### Deployment
### Testing
### Remove Logic From  TheView
#### Learn English

##Intro
It will try to guide you through the process of modeling a fairly complicated portal for Beer rating.
We'll use DSL-platform for our persistence and modeling, Play2 for   

Requirements:

This text assumes you have mono(3.2+), java(8+) and sbt installed.

###DSL-platform

Is a tool for jumping over large cliffs which stand between your model and your applications business logic.
It recognizes mayor repetitive tasks that are happening in any application development:

- connecting your logic with your data model
- evolving your model together with the application
- forcing programming conventions

And solves them using only the simple concepts from DDD.
You will describe your model with a dsl pseudo-language.
Using words like:

- entity - mutable object with identity
- root - entity which encapsulates multiple entities and values to create a single cohesive object
- specification - predicate which describes condition in the system
- domain event - an action that has happened in the system

Platform will guarantee you will have a consistent access to your data.
Throughout your application and specification changes.
You should worry only about the logic your application processes and the presentation of it.

####Simple Model

    module Site {
        root Person {
          String name;
          Int height;
          Int age;
        }
    }

here we express that we want one schema with one root entity containing 2 fields.
"Tell" this to the platform and it will upgrade the database for you.
It will also generate all sources you need to use it correctly.

### Command line client

is a small jar to help you connect to the dsl-platform. Example call would be:

    java -jar dsl-clc.jar
         -u=<some.of.yours@e.mails \
         -p=<useYourImagination> \
         -dsl=dsl \
         -db=localhost:5434/dccTest?user=dccTest&password=dccTest \
         -scala_client=lib/generatedModel.jar \
         -revenj=revenj/generatedModel.dll \
         -sql=migration.sql \
         -download \
         -apply \

or put all those properties in a file and call it with 
 
    java -jar dsl-clc.jar -properties=compiler.props (1)

With this setting client will connect to the platform with a given username and password.
Find all files with `.dsl` extension within directory set with `dsl` property. 
It will then send all such files it finds to the platform.
Platform will respond with the sources and the migration.
Client scala sources will be placed to the temporary folder provided by the system, unless specified otherwise. 
Client will then compile them and place the result of the compilation to the path given with the `scala_client` property.
Output of this operation is a `jar` file you usually want to place in a classpath of your application.
Same with the `C#` server sources, which are hereby called `revenj`.
Finally the `apply` setting means that the database should also be upgraded automatically.
Without the `apply` setting database can be upgraded by hand with an SQL script.
Path to this script can be specified with the sql property.
Other settings can be explored just by calling `java -jar dsl-clc.jar`.

####Generated Code

Specifying the `scala_client` in the properties of the (1) call will create classes that map to your model. 
Yes, its an ORM.
This code depends on a [library](http://www.github.com/ngs-doo/dsl-client-scala) which will handel your repositories.

You will connect to the revenj server with the call to the `Bootstrap.init()` method. 
This method will accept properties which can look like:

    api-url=http://localhost:8999/
    package-name=model

If this file is not specified method will look for it in the classpath or project directory.

####Revenj server

Will connect your library with the database.
After call to (1) revenj directory will be created in project directory.
Connection string to the database needs to be edited before running the service.
It is located in `revenj/Revenj.Http.exe.config`.
Service is ran with

    mono revenj/Revenj.Http.exe

You should now see

    Starting server
    Server running on:
    http://localhost:8999/

#Modeling Beer Rating

###Modeling data

So we have all this people on our site with nothing to do.
Lets make them rate beer!

We'll change our model to resemble our intentions.
And remove the height field because all are equal height under the Beer.

    module RateBeer {
        root Beer {
            String name;
            String beerType;
        }
        root Grade {
            Int     rate;
            Beer    *beer;
            User    *user;
        }
        root User {
            String name;
            Int age;
        } 
    }

Lets apply this schema. Call (1)

Now a client will inform us of what it will do:

    --REMOVE: Site-Person-height
    Property height will be removed from object Person in schema Site
    --CREATE: Site-Beer
    New object Beer will be created in schema Site
    
Since its a destructive migration, it will ask us do we want to proceed:

    Destructive migration detected.
    Apply migration (y/N): 

After applying the migration contents of `Site.Person` will be unchanged.
Apart from missing a field and changing a name.

Every entity will have a field called ID, which is its primary key(PK).
Platform will assign a random ID if its not specified.
This will be explained, a bit later.

####Root References, Foreign Keys

Roots referenced in another root will donate its primary keys to the referencing root.
For example `Grade` entities will have fields `beerID` and `userID` which are FKs.
Generated class Grade will also contain references to `User` and `Beer` instances.
This objects are not set until they are requested within the code.

####Primary Keys

While there can be copycat beers we can't do nothing about.
Users at our site shouldn't share the same `username`s.
So sensible thing to do would be to make a `username` the PK of the User aggregate.
This can be done by signing the field, we want for a PK, next to the roots name definition within regular braces.
Like this...

    root User (username) {
        String username
        ...

This way username becomes User's ID.

Also user shouldn't be able to grade the same beer more than once.
To express this will make Grades PK composite of `userID` and gradeID.
Just place them next to the root name separated with a comma. 

    root Grade(userID, beerID)

No need to declare this fields they are implied.

####Detail


Just to link things better add

    detail grades   from Grade.user;
    detail beers    from Beer.adder;
    
to `User` root.
We don't have to worry it will strain any processes since this data is lazy loaded.

####Presentation

Not rely a part of a model, until you think of a data you want to show.
In dsl-platform `snowflake` is a kind of a view throughout an aggregate.

##Modeling your application

Lets say we want to show some information about the user.
Let it be `name`, `age`, added `beers`, `grades` he gave, average grade he gave.
Maybe even something like average grade of average grades of beers he added...

For this we want to have a view into the User we define

    snowflake UserSnow {

I'll pass it 

        username name;  // this way username is mapped to the name
        grades;
        beers;

For averages I will use a concept called `calculated`.
Within it will call `linq` to express operations on my data.
Linq is highly functional language so I am able to very expressively define.

        calculated double averageGivenGrade from 'it => 
            it.grades
                .Select(g => g.grade)
                .DefaultIfEmpty()
                .Average()';
        calculated double averageBeerAddedGrade from 'it => 
            it.beers
                .Where(beer => beer.grades.Count() > 0 )
                .Select(beer => beer.grades.Average(grade => grade.grade))
                .DefaultIfEmpty()
                .Average()'; 

Close the snowflake section with `}`.
And apply the new DSL.
In the classpath you will now notice there is a new class called `UserSnow`.
Similarly will make `BeerSnow` with information we want to show to the visitors.

##Play

####Authentication
Will eventually be done probably with secureSocial.

##About Twirl
Avoid logic. 
Don't format your code, it will not work formatted.

###To use the source

(1) Make a postgres database caled dccTest with user dccTest pass dccTest at 5432 port.
(2) And call `compile` and `cp Revenj.Http.exe.config` revenj/.
(3) Call `sbt run`.
(4) After (2) is done call `mono revenj/Revenj.Http.exe`.

Your RateBeer is now running at localhost:9000