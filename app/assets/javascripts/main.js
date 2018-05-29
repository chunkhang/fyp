/* globals $, toastr */

$(document).ready(function() {

  /* Classes */

  // Toggle class list
  var subjectListItems = $("#subject-list li");
  var subjectListSmallTexts = $("#subject-list small");
  var classLists = $(".class-lists");
  var lastClicked = -1;
  subjectListItems.click(function() {
    subjectListItems.each(function() {
      $(this).removeClass("active");
    });
    subjectListSmallTexts.each(function() {
      $(this).addClass("text-muted");
    });
    classLists.each(function() {
      $(this).addClass("gone");
    });
    var index = $(this).index();
    if (index != lastClicked) {
      var clickedListItem = $(subjectListItems[index]);
      var clickedSmallText = $(subjectListSmallTexts[index]);
      var classList = $(classLists[index]);
      clickedListItem.addClass("active");
      clickedSmallText.removeClass("text-muted");
      classList.removeClass("gone");
      lastClicked = index;
    } else {
      lastClicked = -1;
    }
  });

  // Fetch classes
  var fetchButton = $("#fetch-button");
  fetchButton.click(function() {
    var fetchSpinner = $("#fetch-spinner");
    fetchButton.addClass("gone");
    fetchSpinner.removeClass("gone");
    setTimeout(function() {
      $.ajax({
        method: "GET",
        url: "/classes/fetch",
        dataType: "json",
        timeout: 3000,
        success: function(response) {
          fetchSpinner.addClass("gone");
          if (response.status == "success") {
            toastr.success("Fetched new classes");
            setTimeout(function() {
              location.reload();
            }, 1500);
          } else {
            toastr.error(response.reason);
          }
        },
        error: function() {
          fetchSpinner.addClass("gone");
          fetchButton.removeClass("gone");
          toastr.error("Something went wrong");
        }
      });
    }, 1000);
  });

});
