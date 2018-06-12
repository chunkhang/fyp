/* globals $, moment, toastr */

export function calendar() {

  var calendar = $("#calendar");
  var sources = {
    month: {
      url: "/calendar/month/events",
      type: "GET",
      error: function() {
        console.log("Failed to fetch calendar month events");
      }
    },
    agendaWeek: {
      url: "/calendar/week/events",
      type: "GET",
      error: function() {
        console.log("Failed to fetch calendar week events");
      }
    },
    listWeek: {
      url: "/calendar/list/events",
      type: "GET",
      error: function() {
        console.log("Failed to fetch calendar list events");
      }
    },
  };
  var taskSources = {
    month: {
      url: "/calendar/month/tasks",
      type: "GET",
      error: function() {
        console.log("Failed to fetch calendar month tasks");
      }
    },
    agendaWeek: {
      url: "/calendar/week/tasks",
      type: "GET",
      error: function() {
        console.log("Failed to fetch calendar week tasks");
      }
    },
    listWeek: {
      url: "/calendar/list/tasks",
      type: "GET",
      error: function() {
        console.log("Failed to fetch calendar list tasks");
      }
    },
  };
  var lastView = "";
  var eventModal = $("#event-modal");
  var eventModalTitle = $("#event-modal-title");
  var eventModalSubject = $("#event-modal-subject");
  var eventModalClass = $("#event-modal-class");
  var eventModalDate = $("#event-modal-date");
  var eventModalTime = $("#event-modal-time");
  var eventModalVenue = $("#event-modal-venue");
  var eventModalCancelButton = $("#event-modal-cancel");
  var eventModalReplaceButton = $("#event-modal-replace");
  var eventModalBackButton = $("#event-modal-back");
  var eventModalConfirmCancellationButton = $("#event-modal-confirm-cancel");
  var eventModalFindReplacementButton = $("#event-modal-find");
  var eventModalConfirmReplacementButton =
    $("#event-modal-confirm-replacement");
  var eventModalSpinner = $("#event-modal-spinner");
  var eventModalForm = $("#event-modal-form");
  var eventModalInfo = $("#event-modal-info");
  var eventModalReplacement = $("#event-modal-replacement");
  var eventModalReplacementMessage = $("#event-modal-replacement-message");
  var eventModalReplacementInfo = $("#event-modal-replacement-info");
  var eventModalReplacementDate = $("#event-modal-replacement-date");
  var eventModalReplacementTime = $("#event-modal-replacement-time");
  var eventModalReplacementVenue = $("#event-modal-replacement-venue");
  var eventModalReplacementBar = $("#event-modal-replacement-bar");
  var eventModalReplacementPercentage =
    $("#event-modal-replacement-percentage");
  var eventModalReplacementStudents =
    $("#event-modal-replacement-students");
  var eventModalFrom = $("#event-modal-from");
  var eventModalTo = $("#event-modal-to");
  var originalTitle = eventModalTitle.text();
  var eventModalFooter = $("#event-modal-footer");

  // Initialize tooltip
  $("#event-modal-subject").tooltip();

  // Initialize calendar
  calendar.fullCalendar({
    schedulerLicenseKey: "CC-Attribution-NonCommercial-NoDerivatives",
    eventSources: [
      sources.month,
      sources.agendaWeek,
      sources.listWeek,
      taskSources.month,
      taskSources.agendaWeek,
      taskSources.listWeek
    ],
    header: {
      left: "month,agendaWeek,listWeek",
      center: "title"
    },
    buttonText: {
      month: "Month",
      agendaWeek: "Week",
      listWeek: "List",
      today: "Today"
    },
    nowIndicator: true,
    businessHours: {
      dow: [1, 2, 3, 4, 5, 6],
      start: "08:00",
      end: "18:00"
    },
    minTime: "08:00:00",
    maxTime: "18:00:00",
    contentHeight: "auto",
    eventLimit: true,
    timeFormat: "hh:mmA",
    views: {
      agendaWeek: {
        columnHeaderFormat: "ddd, D/M",
        slotLabelFormat: "hh:mmA",
        allDayText: "Tasks"
      },
      listWeek: {
        listDayAltFormat: "MMM D, YYYY",
        noEventsMessage: "Nothing to display",
        allDayText: "Task"
      }
    },
    slotEventOverlap: false,
    viewRender: function(view) {
      if (view.name != lastView) {
        // Change event source
        calendar.fullCalendar("removeEventSources");
        calendar.fullCalendar("addEventSource", sources[view.name]);
        calendar.fullCalendar("addEventSource", taskSources[view.name]);
        lastView = view.name;
      }
    },
    eventDataTransform: function(eventData) {
      var event = eventData;
      if ("modalReplacement" in event && event.modalReplacement) {
        // Green replacements
        event.backgroundColor = "#3f9b68";
      } else if ("modalScore" in event) {
        // Red tasks
        event.backgroundColor = "#9b3f72";
      }
      return event;
    },
    eventAfterRender: function(event, element) {
      var end = event.end == null ? moment(event.modalEnd) : event.end;
      var eventAlreadyPast = end.isSameOrBefore(moment());
      if (!eventAlreadyPast) {
        if (event.end != null) {
          // Enable event modal
          element.attr("data-toggle", "modal");
          element.attr("data-target", "#event-modal");
        } else {
          // Enable add task modal
          element.attr("data-toggle", "modal");
          element.attr("data-target", "#add-task-modal");
        }
      } else {
        // Blur past events
        element.css("opacity", "0.5");
        element.css("cursor", "default");
      }
    },
    eventClick: function(event) {
      if (event.end != null) {
        // Populate event modal
        eventModalSubject.text(event.modalSubjectCode);
        eventModalSubject.attr("data-original-title", event.modalSubjectName);
        eventModalClass.text(event.modalClass);
        eventModalDate.text(event.modalDate);
        eventModalTime.text(event.modalTime);
        eventModalVenue.text(event.modalVenue);
        eventModal.data("classId", event.modalClassId);
        eventModal.data("date", event.modalDate);
        eventModal.data("databaseDate", event.modalDatabaseDate);
        eventModalCancelButton.removeClass("gone");
        eventModalReplaceButton.removeClass("gone");
        eventModal.data("replacement", event.modalReplacement);
      } else {
        addTaskModal.data("modalTask", event.modalTask);
        addTaskModal.data("modalSubject", event.modalSubject);
        addTaskModal.data("modalTitle", event.modalTitle);
        addTaskModal.data("modalDate", event.modalDate);
        addTaskModal.data("modalScore", event.modalScore);
        addTaskModal.data("modalDescription", event.modalDescription);
      }
    }
  });

  // Cancel class
  eventModalCancelButton.click(function() {
    eventModalTitle.text("Cancel Class");
    eventModalCancelButton.addClass("gone");
    eventModalReplaceButton.addClass("gone");
    eventModalBackButton.removeClass("gone");
    eventModalConfirmCancellationButton.removeClass("gone");
    eventModalDate.css({"text-decoration": "line-through"});
    eventModalTime.css({"text-decoration": "line-through"});
    eventModalVenue.css({"text-decoration": "line-through"});
  });

  // Confirm cancellation
  eventModalConfirmCancellationButton.click(function() {
    var classId = eventModal.data("classId");
    var payload = {
      "date": eventModal.data("date")
    };
    eventModalBackButton.addClass("gone");
    eventModalConfirmCancellationButton.addClass("gone");
    eventModalSpinner.removeClass("gone");
    // Send request
    setTimeout(function() {
      $.ajax({
        method: "POST",
        url: `/classes/${classId}/cancel`,
        contentType: "application/json",
        dataType: "json",
        data: JSON.stringify(payload),
        timeout: 3000,
        success: function(response) {
          calendar.fullCalendar("refetchEvents");
          eventModalSpinner.addClass("gone");
          if (response.status == "success") {
            toastr.success("Class cancelled");
          }
          setTimeout(function() {
            eventModal.click();
          }, 1000);
        },
        error: function() {
          eventModalSpinner.addClass("gone");
          toastr.error("Something went wrong");
          setTimeout(function() {
            eventModal.click();
          }, 1000);
        }
      });
    }, 1000);
  });

  // Replace class
  eventModalReplaceButton.click(function() {
    eventModalTitle.text("Replace class");
    eventModalCancelButton.addClass("gone");
    eventModalReplaceButton.addClass("gone");
    eventModalBackButton.removeClass("gone");
    eventModalFindReplacementButton.removeClass("gone");
    eventModalInfo.addClass("gone");
    eventModalForm.removeClass("gone");
    eventModalReplacement.removeClass("gone");
  });

  // Find replacement
  eventModalFindReplacementButton.click(function() {
    // Validate from and to
    var from = eventModalFrom[0].value;
    var to = eventModalTo[0].value;
    if (from != "" && to != "") {
      eventModalFrom.removeClass("invalid");
      eventModalTo.removeClass("invalid");
      var classId = eventModal.data("classId");
      var payload = {
        "originalDate": eventModal.data("date"),
        "startDate": from,
        "endDate": to
      };
      eventModalBackButton.addClass("gone");
      eventModalFindReplacementButton.addClass("gone");
      eventModalSpinner.removeClass("gone");
      eventModalForm.find("input").attr("disabled", "disabled");
      // Send request
      setTimeout(function() {
        $.ajax({
          method: "POST",
          url: `/classes/${classId}/find`,
          contentType: "application/json",
          dataType: "json",
          data: JSON.stringify(payload),
          timeout: 3000,
          success: function(response) {
            eventModalSpinner.addClass("gone");
            eventModalBackButton.removeClass("gone");
            if (response.status == "success") {
              eventModalConfirmReplacementButton.removeClass("gone");
              eventModalReplacementInfo.removeClass("gone");
              eventModalReplacementDate.text(response.date);
              eventModalReplacementTime.text(response.time);
              eventModalReplacementVenue.text(response.venue);
              eventModalReplacementBar.removeClass("gone");
              eventModal.data("replacementDate", response.date);
              eventModal.data("replacementTime", response.time);
              eventModal.data("replacementVenue", response.venue);
              var percentage = Math.round(
                (response.availableStudents / response.allStudents) * 100
              );
              eventModalReplacementPercentage.css({"width": `${percentage}%`});
              eventModalReplacementStudents.text(
                `${response.availableStudents} / ${response.allStudents} ` +
                "students available"
              );
            } else {
              eventModalReplacementMessage.text("No replacement slot found");
            }
          },
          error: function() {
            eventModalReplacementMessage.text("Something went wrong");
            eventModalBackButton.removeClass("gone");
            eventModalSpinner.addClass("gone");
          }
        });
      }, 1000);
    } else {
      eventModalFrom.addClass("invalid");
      eventModalTo.addClass("invalid");
    }
  });

  // Confirm replacement
  eventModalConfirmReplacementButton.click(function() {
    var classId = eventModal.data("classId");
    var payload = {
      "cancelledDate": eventModal.data("date"),
      "replacementDate": eventModal.data("replacementDate"),
      "time": eventModal.data("replacementTime"),
      "venue": eventModal.data("replacementVenue")
    };
    eventModalBackButton.addClass("gone");
    eventModalConfirmReplacementButton.addClass("gone");
    eventModalSpinner.removeClass("gone");
    // Send request
    setTimeout(function() {
      $.ajax({
        method: "POST",
        url: `/classes/${classId}/replace`,
        contentType: "application/json",
        dataType: "json",
        data: JSON.stringify(payload),
        timeout: 3000,
        success: function(response) {
          calendar.fullCalendar("refetchEvents");
          eventModalSpinner.addClass("gone");
          if (response.status == "success") {
            toastr.success("Class replaced");
          }
          setTimeout(function() {
            eventModal.click();
          }, 1000);
        },
        error: function() {
          eventModalSpinner.addClass("gone");
          toastr.error("Something went wrong");
          setTimeout(function() {
            eventModal.click();
          }, 1000);
        }
      });
    }, 1000);
  });

  // Back
  eventModalBackButton.click(function() {
    backToOriginal();
  });

  // Revert event modal to original state
  function backToOriginal() {
    eventModalTitle.text(originalTitle);
    eventModalCancelButton.removeClass("gone");
    eventModalReplaceButton.removeClass("gone");
    eventModalBackButton.addClass("gone");
    eventModalConfirmCancellationButton.addClass("gone");
    eventModalFindReplacementButton.addClass("gone");
    eventModalConfirmReplacementButton.addClass("gone");
    eventModalFrom.removeClass("invalid");
    eventModalTo.removeClass("invalid");
    eventModalForm.addClass("gone");
    eventModalForm.find("input").removeAttr("disabled");
    eventModalInfo.removeClass("gone");
    eventModalReplacement.addClass("gone");
    eventModalReplacementMessage.text("");
    eventModalReplacementInfo.addClass("gone");
    eventModalReplacementBar.addClass("gone");
    eventModalReplacementStudents.text("");
    eventModalDate.css({"text-decoration": "none"});
    eventModalTime.css({"text-decoration": "none"});
    eventModalVenue.css({"text-decoration": "none"});
    eventModalFooter.removeClass("gone");
  }

  eventModal.on("show.bs.modal", function() {
    // Disable scrolling
    $("html").addClass("scroll-lock");
    backToOriginal();
    // Replacement events
    if (eventModal.data("replacement")) {
      eventModalTitle.text("View replacement class");
      eventModalCancelButton.addClass("gone");
      eventModalReplaceButton.addClass("gone");
      eventModalFooter.addClass("gone");
    }
  });
  eventModal.on("hide.bs.modal", function() {
    // Enable scrolling
    $("html").removeClass("scroll-lock");
  });

  var addTaskModal = $("#add-task-modal");
  var addTaskModalTitle = $("#add-task-modal-title");
  var addTaskCancelButton = $("#add-task-cancel");
  var addTaskConfirmButton = $("#add-task-confirm");
  var addTaskDeleteButton = $("#add-task-delete");
  var addTaskEditButton = $("#add-task-edit");
  var addTaskSpinner = $("#add-task-spinner");
  var addTaskSubject = $("#add-task-subject");
  var addTaskTitle = $("#add-task-title");
  var addTaskScore = $("#add-task-score");
  var addTaskDate = $("#add-task-date");
  var addTaskDescription = $("#add-task-description");

  // Validate task input
  function validateTaskInput() {
    addTaskModal.find("input").removeClass("invalid");
    var validInput = true;
    var title = addTaskTitle.val();
    var score = addTaskScore.val();
    var dueDate = addTaskDate.val();
    var description = addTaskDescription.val();
    if (title.length == 0 || title.length > 20) {
      addTaskTitle.addClass("invalid");
      validInput = false;
    }
    if (isNaN(parseInt(score)) || parseInt(score) < 1 || parseInt(score) > 100) {
      addTaskScore.addClass("invalid");
      validInput = false;
    }
    if (dueDate == "") {
      addTaskDate.addClass("invalid");
      validInput = false;
    }
    if (description.length > 0 && description.length > 120) {
      addTaskDescription.addClass("invalid");
      validInput = false;
    }
    return validInput;
  }

  // Task payload
  function getTaskPayload() {
    return {
        "title": addTaskTitle.val(),
        "score": parseInt(addTaskScore.val()),
        "dueDate": addTaskDate.val(),
        "description": addTaskDescription.val()
    };
  }

  // Confirm add task
  addTaskConfirmButton.click(function() {
    var subjectId = addTaskSubject.val();
    if (validateTaskInput()) {
      var payload = getTaskPayload();
      addTaskCancelButton.addClass("gone");
      addTaskConfirmButton.addClass("gone");
      addTaskSpinner.removeClass("gone");
      // Send request
      setTimeout(function() {
        $.ajax({
          method: "POST",
          url: `/subjects/${subjectId}/tasks`,
          contentType: "application/json",
          dataType: "json",
          data: JSON.stringify(payload),
          timeout: 3000,
          success: function(response) {
            calendar.fullCalendar("refetchEvents");
            addTaskSpinner.addClass("gone");
            if (response.status == "success") {
              toastr.success("Task added");
            }
            setTimeout(function() {
              addTaskModal.click();
            }, 1000);
          },
          error: function() {
            addTaskSpinner.addClass("gone");
            toastr.error("Something went wrong");
            setTimeout(function() {
              addTaskModal.click();
            }, 1000);
          }
        });
      }, 1000);
    }
  });

  // Cancel add task
  addTaskCancelButton.click(function() {
    addTaskModal.click();
  });

  // Edit task
  addTaskEditButton.click(function() {

  });

  // Delete task
  addTaskDeleteButton.click(function() {
    addTaskDeleteButton.addClass("gone");
    addTaskEditButton.addClass("gone");
    addTaskSpinner.removeClass("gone");
    var subjectId = addTaskSubject.val();
    var payload = {
      "taskId": addTaskModal.data("modalTask")
    };
    // Send request
    setTimeout(function() {
      $.ajax({
        method: "POST",
        url: `/subjects/${subjectId}/tasks/delete`,
        contentType: "application/json",
        dataType: "json",
        data: JSON.stringify(payload),
        timeout: 3000,
        success: function(response) {
          calendar.fullCalendar("refetchEvents");
          addTaskSpinner.addClass("gone");
          if (response.status == "success") {
            toastr.success("Task deleted");
          }
          setTimeout(function() {
            addTaskModal.click();
          }, 1000);
        },
        error: function() {
          addTaskSpinner.addClass("gone");
          toastr.error("Something went wrong");
          setTimeout(function() {
            addTaskModal.click();
          }, 1000);
        }
      });
    }, 1000);
  });

  addTaskModal.on("show.bs.modal", function(event) {
    // Disable scrolling
    $("html").addClass("scroll-lock");
    if (event.relatedTarget.id == "add-task-button") {
      addTaskModalTitle.text("Add task");
      addTaskCancelButton.removeClass("gone");
      addTaskConfirmButton.removeClass("gone");
      addTaskDeleteButton.addClass("gone");
      addTaskEditButton.addClass("gone");
      // Clear form fields
      addTaskModal.find(".clearable").val("");
      addTaskModal.find("input").removeClass("invalid");
    } else {
      addTaskModalTitle.text("View task");
      addTaskCancelButton.addClass("gone");
      addTaskConfirmButton.addClass("gone");
      addTaskDeleteButton.removeClass("gone");
      addTaskEditButton.removeClass("gone");
      // Fill form fields
      $(`.select-dropdown li:contains(${addTaskModal.data("modalSubject")})`)
        .trigger("click");
      addTaskTitle.val(addTaskModal.data("modalTitle"));
      addTaskScore.val(addTaskModal.data("modalScore"));
      addTaskDate.pickadate("picker").set(
        "select",
        addTaskModal.data("modalDate")
      );
      addTaskDescription.val(addTaskModal.data("modalDescription"));
    }
  });
  addTaskModal.on("hide.bs.modal", function() {
    // Enable scrolling
    $("html").removeClass("scroll-lock");
  });

}
