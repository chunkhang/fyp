/* globals $ */

export function calendar() {

  // Initialize calendar
  var calendar = $("#calendar");
  initializeCalender();

  function initializeCalender() {
    calendar.fullCalendar({
      schedulerLicenseKey: "CC-Attribution-NonCommercial-NoDerivatives",
      eventSources: [
        {
          url: "/calendar/events",
          type: "GET",
          error: function() {
            console.log("Failed to fetch calendar events");
          }
        }
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
      minTime: "08:00:00",
      maxTime: "18:00:00",
      hiddenDays: [0],
      contentHeight: "auto",
      dayClick: handleClickDay
    });
  }

  function handleClickDay(date, event, view, resource) {
    if (view.name == "month") {
      // Open timeline view
      calendar.fullCalendar("gotoDate", date.format());
      calendar.fullCalendar("changeView", "timelineDay");
    }
  }

}
