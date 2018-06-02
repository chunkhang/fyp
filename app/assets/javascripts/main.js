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

  /* Forms */

  // Material select
  $(".mdb-select").material_select();

  // Material time picker
  var timePickers = $(".timepicker");
  timePickers.each(function() {
    var defaultTime = $(this).val();
    $(this).pickatime({
      twelvehour: true,
      default: defaultTime === "" ? "08:00AM" : defaultTime
    });
  });
  var errors = $("error");
  if (errors.length != 0) {
    errors.each(function() {
      toastr.error($(this).text());
    });
    timePickers.each(function() {
      $(this).addClass("invalid");
    });
  }

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

  // Fetch button
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

  // Toggle lists
  var subjectItems = $("#subject-list li");
  var classLists = $(".class-lists");
  var classItems = $(".class-lists li");
  var studentLists = $(".student-lists");
  var currentSubject = null;
  var currentClass = null;
  subjectItems.click(function() {
    subjectItems.each(function() {
      deactivateItem($(this));
    });
    classLists.each(function() {
      $(this).addClass("gone");
    });
    classItems.each(function() {
      $(this).removeClass("active");
    });
    studentLists.each(function() {
      $(this).addClass("gone");
    });
    var subject = $(this).data("self");
    if (currentSubject != subject) {
      // Opening subject
      activateItem($(this));
      var classList = $(classLists.filter(`[data-parent="${subject}"]`));
      classList.removeClass("gone");
      currentSubject = subject;
    } else {
      // Closing subject
      classItems.each(function() {
        deactivateItem($(this));
      });
      currentSubject = null;
      currentClass = null;
    }
  });
  classItems.click(function() {
    classItems.each(function() {
      deactivateItem($(this));
    });
    studentLists.each(function() {
      $(this).addClass("gone");
    });
    var class_ = $(this).data("self");
    if (currentClass != class_) {
      // Opening class
      activateItem($(this));
      var studentList = $(studentLists.filter(`[data-parent="${class_}"]`));
      console.log(studentList);
      studentList.removeClass("gone");
      currentClass = class_;
    } else {
      // Closing class
      currentClass = null;
    }
  });
  function activateItem(item) {
    item.addClass("active");
    item.find("small").first().removeClass("text-muted");
    item.find("a").first().removeClass("gone");
    item.find("div").first().addClass("truncate");
  }
  function deactivateItem(item) {
    item.removeClass("active");
    item.find("small").first().addClass("text-muted");
    item.find("a").first().addClass("gone");
    item.find("div").first().removeClass("truncate");
  }

  // Edit buttons
  var editButtons = $(".edit-buttons");
  editButtons.click(function(event) {
    event.stopPropagation();
  });

});
