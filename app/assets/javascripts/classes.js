/* globals $, toastr */

export function classes() {

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

}
