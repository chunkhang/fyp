/* globals $, toastr */

$(document).ready(function() {

  /* Miscellaneous */

  // Add active class to navigation tab
  var activeIndex= parseInt($("active").first().text());
  if (activeIndex != -1) {
    var activeTab = $("#navigation-tabs").children()[activeIndex];
    $(activeTab).addClass("active");
  }

  // Close flash messages automatically
  setTimeout(function() {
    $(".alert").alert("close");
  }, 3000);

  // Remove all but first student label
  var studentLabels = $(".student-labels");
  var first = true;
  studentLabels.each(function() {
    if (!first) {
      $(this).remove();
    } else {
      first = false;
    }
  });

  /* Forms */

  // Select chosen value for select input
  var selects = $("select");
  selects.each(function() {
    var defaultOption;
    if ($(this).is("[chosen]")) {
      var selectedValue = $(this).attr("chosen");
      var selectedOption = $(`option[value="${selectedValue}"]`)[0];
      $(selectedOption).attr("selected", "");
      defaultOption = `<option value="" disabled>Choose a ${$(this).attr("id")}</option>`;
    } else {
      defaultOption = `<option value="" disabled selected>Choose a ${$(this).attr("id")}</option>`;
    }
    $(this).prepend(defaultOption);
  });

  // Initialize material select
  $(".mdb-select").material_select();

  // Initialize material time picker
  $("#time").pickatime({
    autoclose: true,
    "default": "now"
  });

  // Disable enter to submit
  $(window).keydown(function(event){
    if (event.keyCode == 13) {
      event.preventDefault();
    }
  });

  // Add invalid classes to inputs
  var inputs = $("input");
  inputs.each(function() {
    if ($(this).siblings("invalid").length == 1) {
      $(this).addClass("invalid");
    }
  });

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
          toastr[response.status](response.message);
          if (response.status == "success") {
            setTimeout(function() {
              location.reload();
            }, 2000);
          } else {
            fetchButton.removeClass("gone");
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
