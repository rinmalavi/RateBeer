angular.module('listBeerApp', ['ui.bootstrap'])
.controller('listBeerCtrl', function($scope, $modal, $http) {
    $scope.ids = [];
    $scope.grade = function(id, name) {
        $modal.open({
            templateUrl: 'assets/dialog/grade.html',
            controller: 'gradeInst',
            resolve: {
                beername: function () {
                    return name;
                }
             }
        }).result.then( function (g) {
            $http.post('/api/grade/' + id, g)
            .then(
                function(resp) {
                    $('div.info').html('Added grade');
                    $('td.grade' + id).html(g.grade);
                },
                function(resp) {
                    $('div.info').html('Failed grading ' + resp);
                }
            );
        });
    }
})
.controller('addBeerCtrl', function($scope, $modal, $http) {
    $scope.addBeer = function() {
        $modal.open({
            templateUrl: 'assets/dialog/beer.html',
            controller: 'beerInst'
        }).result.then( function (b) {
            $http.post('/api/newBeer', b)
            .then(
                function(resp) {
                    $('div.info').html('Added beer successful.');
                },
                function(resp) {
                    $('div.info').html('Adding beer failed');
                }
            )
        })
    }
})
.controller('gradeInst', function($scope, $modalInstance, beername) {
    $scope.beername = beername;
    $scope.ok = function () {
        var result = {
            "grade" : $scope.grade,
            "description" : $scope.description,
            "tags" : $scope.tags
        };
        $modalInstance.close(result);
    };

    $scope.cancel = function () {
        $modalInstance.dismiss('cancel');
    };
})
.controller('beerInst', function($scope, $modalInstance) {
    $scope.ok = function () {
        var result = {
            "name" : $scope.name,
            "type" : $scope.type
        };
        $modalInstance.close(result);
    };

    $scope.cancel = function () {
        $modalInstance.dismiss('cancel');
    };
});
