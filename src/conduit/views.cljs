(ns conduit.views
  (:require [reagent.core  :as reagent]
            [conduit.router :refer [url-for set-token!]]
            [re-frame.core :refer [subscribe dispatch]]
            [clojure.string :as str :refer [trim split join]]))

(defn errors-list
  [errors]
  [:ul.error-messages
   (for [[key [val]] errors]
     ^{:key key} [:li (str (name key) " " val)])])

;; -- Header ------------------------------------------------------------------
;;
(defn header
  []
  (let [user        @(subscribe [:user])
        active-page @(subscribe [:active-page])]
    [:nav.navbar.navbar-light
     [:div.container
      [:a.navbar-brand {:href (url-for :home)} "conduit"]
      (if (empty? user)
        [:ul.nav.navbar-nav.pull-xs-right
         [:li.nav-item
          [:a.nav-link {:href (url-for :home) :class (when (= active-page :home) "active")} "Home"]]
         [:li.nav-item
          [:a.nav-link {:href (url-for :login) :class (when (= active-page :login) "active")} "Sign in"]]
         [:li.nav-item
          [:a.nav-link {:href (url-for :register) :class (when (= active-page :register) "active")} "Sign up"]]]
        [:ul.nav.navbar-nav.pull-xs-right
         [:li.nav-item
          [:a.nav-link {:href (url-for :home) :class (when (= active-page :home) "active")} "Home"]]
         [:li.nav-item
          [:a.nav-link {:href (url-for :settings) :class (when (= active-page :settings) "active")}
           [:i.ion-gear-a "Settings"]]]
         [:li.nav-item
          [:a.nav-link {:href (url-for :profile :user-id (:username user)) :class (when (= active-page :profile) "active")} (:username user)
           [:img.user-pic {:src (:image user)}]]]])]]))

;; -- Footer ------------------------------------------------------------------
;;
(defn footer
  []
  [:footer
   [:div.container
    [:a.logo-font {:href (url-for :home)} "conduit"]
    [:span.attribution
     "An interactive learning project from "
     [:a {:href "https://thinkster.io"} "Thinkster"]
     ". Code & design licensed under MIT."]]])

;; -- Home --------------------------------------------------------------------
;;

(defn home
  []
  (let [loading        @(subscribe [:loading])
        user           @(subscribe [:user])]
    [:div.home-page
     (when (empty? user)
       [:div.banner
        [:div.container
         [:h1.logo-font "conduit"]
         [:p "A place to share your knowledge."]]])
     [:div.container.page
      [:h4 (str user)]]]))

;; -- Login -------------------------------------------------------------------
;;
(defn login-user [event credentials]
  (.preventDefault event)
  (dispatch [:login credentials]))

(defn login
  []
  (let [default {:email "" :password ""}
        credentials (reagent/atom default)]
    (fn []
      (let [{:keys [email password]} @credentials
            loading  @(subscribe [:loading])
            errors   @(subscribe [:errors])]
        [:div.auth-page
         [:div.container.page
          [:div.row
           [:div.col-md-6.offset-md-3.col-xs-12
            [:h1.text-xs-center "Sign in"]
            [:p.text-xs-center
             [:a {:href (url-for :register)} "Need an account?"]]
            (when (:login errors)
              [errors-list (:login errors)])
            [:form {:on-submit #(login-user % @credentials)}
             [:fieldset.form-group
              [:input.form-control.form-control-lg {:type "text"
                                                    :placeholder "Email"
                                                    :value email
                                                    :on-change #(swap! credentials assoc :email (-> % .-target .-value))
                                                    :disabled (when (:login loading))}]]

             [:fieldset.form-group
              [:input.form-control.form-control-lg {:type "password"
                                                    :placeholder "Password"
                                                    :value password
                                                    :on-change #(swap! credentials assoc :password (-> % .-target .-value))
                                                    :disabled (when (:login loading))}]]
             [:button.btn.btn-lg.btn-primary.pull-xs-right {:class (when (:login loading) "disabled")} "Sign in"]]]]]]))))

;; -- Register ----------------------------------------------------------------
;;
(defn register-user [event registration]
  (.preventDefault event)
  (dispatch [:register-user registration]))

(defn register
  []
  (let [default {:username "" :email "" :password ""}
        registration (reagent/atom default)]
    (fn []
      (let [{:keys [username email password]} @registration
            loading  @(subscribe [:loading])
            errors   @(subscribe [:errors])]
        [:div.auth-page
         [:div.container.page
          [:div.row
           [:div.col-md-6.offset-md-3.col-xs-12
            [:h1.text-xs-center "Sign up"]
            [:p.text-xs-center
             [:a {:href (url-for :login)} "Have an account?"]]
            (when (:register-user errors)
              [errors-list (:register-user errors)])
            [:form {:on-submit #(register-user % @registration)}
             [:fieldset.form-group
              [:input.form-control.form-control-lg {:type "text"
                                                    :placeholder "Your Name"
                                                    :value username
                                                    :on-change #(swap! registration assoc :username (-> % .-target .-value))
                                                    :disabled (when (:register-user loading))}]]
             [:fieldset.form-group
              [:input.form-control.form-control-lg {:type "text"
                                                    :placeholder "Email"
                                                    :value email
                                                    :on-change #(swap! registration assoc :email (-> % .-target .-value))
                                                    :disabled (when (:register-user loading))}]]
             [:fieldset.form-group
              [:input.form-control.form-control-lg {:type "password"
                                                    :placeholder "Password"
                                                    :value password
                                                    :on-change #(swap! registration assoc :password (-> % .-target .-value))
                                                    :disabled (when (:register-user loading))}]]
             [:button.btn.btn-lg.btn-primary.pull-xs-right {:class (when (:register-user loading) "disabled")} "Sign up"]]]]]]))))

;; -- Profile -----------------------------------------------------------------
;;
(defn profile
  []
  (let [{:keys [image username bio] :or {username ""}} @(subscribe [:profile])
        loading  @(subscribe [:loading])
        user     @(subscribe [:user])]
    [:div.profile-page
     [:div.user-info
      [:div.container
       [:div.row
        [:div.col-xs-12.col-md-10.offset-md-1
         [:img.user-img {:src image}]
         [:h4 username]
         [:p bio]
         (if (= (:username user) username)
           [:a.btn.btn-sm.btn-outline-secondary.action-btn {:href (url-for :settings)}
            [:i.ion-gear-a] " Edit Profile Settings"]
            )]]]]
     [:div.container
         [:h4 user]]]))

;; -- Settings ----------------------------------------------------------------
;;
(defn logout-user [event]
  (.preventDefault event)
  (dispatch [:logout]))

(defn update-user [event update]
  (.preventDefault event)
  (dispatch [:update-user update]))

(defn settings
  []
  (let [{:keys [bio email image username] :as user} @(subscribe [:user])
        default     {:bio bio :email email :image image :username username}
        loading     @(subscribe [:loading])
        user-update (reagent/atom default)]
    [:div.settings-page
     [:div.container.page
      [:div.row
       [:div.col-md-6.offset-md-3.col-xs-12
        [:h1.text-xs-center "Your Settings"]
        [:form
         [:fieldset
          [:fieldset.form-group
           [:input.form-control {:type "text"
                                 :placeholder "URL of profile picture"
                                 :default-value (:image user)
                                 :on-change #(swap! user-update assoc :image (-> % .-target .-value))}]]
          [:fieldset.form-group
           [:input.form-control.form-control-lg {:type "text"
                                                 :placeholder "Your Name"
                                                 :default-value (:username user)
                                                 :on-change #(swap! user-update assoc :username (-> % .-target .-value))
                                                 :disabled (when (:update-user loading))}]]
          [:fieldset.form-group
           [:textarea.form-control.form-control-lg {:rows "8"
                                                    :placeholder "Short bio about you"
                                                    :default-value (:bio user)
                                                    :on-change #(swap! user-update assoc :bio (-> % .-target .-value))
                                                    :disabled (when (:update-user loading))}]]
          [:fieldset.form-group
           [:input.form-control.form-control-lg {:type "text"
                                                 :placeholder "Email"
                                                 :default-value (:email user)
                                                 :on-change #(swap! user-update assoc :email (-> % .-target .-value))
                                                 :disabled (when (:update-user loading))}]]
          [:fieldset.form-group
           [:input.form-control.form-control-lg {:type "password"
                                                 :placeholder "Password"
                                                 :default-value ""
                                                 :on-change #(swap! user-update assoc :password (-> % .-target .-value))
                                                 :disabled (when (:update-user loading))}]]
          [:button.btn.btn-lg.btn-primary.pull-xs-right {:on-click #(update-user % @user-update)
                                                         :class (when (:update-user loading) "disabled")} "Update Settings"]]]
        [:hr]
        [:button.btn.btn-outline-danger {:on-click #(logout-user %)} "Or click here to logout."]]]]]))

(defn pages [page-name]
  (case page-name
    :home     [home]
    :login    [login]
    :register [register]
    :profile  [profile]
    :settings [settings]
    [home]))

(defn conduit-app
  []
  (let [active-page @(subscribe [:active-page])]
    [:div
     [header]
     [pages active-page]
     [footer]]))
