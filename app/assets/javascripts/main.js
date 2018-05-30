/* globals $, toastr */

$(document).ready(function() {

  /* Classes */
  var fetchButton = $("#fetch-button");
  var subjectItems = $("#subject-list li");
  var subjectSubtitles = $(".subject-subtitles");
  var subjectEditButtons = $(".subject-edits");
  var classLists = $(".class-lists");

  // Fetch button
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
            }, 2000);
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

  // Toggle subject list
  var lastSubjectClicked = -1;
  subjectItems.click(function() {
    subjectItems.each(function() {
      $(this).removeClass("active");
    });
    subjectSubtitles.each(function() {
      $(this).addClass("text-muted");
    });
    subjectEditButtons.each(function() {
      $(this).addClass("gone");
    });
    classLists.each(function() {
      $(this).addClass("gone");
    });
    var index = $(this).index();
    if (index != lastSubjectClicked) {
      var clickedItem = $(subjectItems[index]);
      var clickedSmallText = $(subjectSubtitles[index]);
      var editButton = $(subjectEditButtons[index]);
      var classList = $(classLists[index]);
      clickedItem.addClass("active");
      clickedSmallText.removeClass("text-muted");
      editButton.removeClass("gone");
      classList.removeClass("gone");
      lastSubjectClicked = index;
    } else {
      lastSubjectClicked = -1;
    }
  });

  // Edit buttons
  subjectEditButtons.click(function(event) {
    event.stopPropagation();
  });

});
