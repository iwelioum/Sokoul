<?php
/**
 * colors
 *
 * @package fmovie
 */

// color style
add_action('wp_head', 'my_color_style', 100);
function my_color_style()
{ 
$color_style = get_option('admin_color_style');
if ($color_style == 'green') { 
$color = '#00acc1';
} elseif ($color_style == 'blue') { 
$color = '#2e7bcf';
} elseif ($color_style == 'black') { 
$color = '#626060';
} elseif ($color_style == 'red') { 
$color = '#e50914';
} elseif ($color_style == 'purple') { 
$color = '#5a2e98';
} elseif ($color_style == 'cherry') { 
$color = '#e1216d';
} elseif ($color_style == 'pink') { 
$color = '#e83e8c';
} elseif ($color_style == 'yellow') { 
$color = '#79c142';
} elseif ($color_style == 'orange') { 
$color = '#fd7e14';
} elseif ($color_style == 'light') { 
$color = '#3e8afa';
}else {
$color = '#00acc1';
}
?>
<?php if ($color == 'green') { ?>
<?php } else { ?>
<style>
:root {

    --primary: <?php echo $color; ?> !important;

}
<?php if ($color_style == 'red') { ?>
#episodes #servers .server.active > span{color:#000}#episodes .episodes .episode a.active,.tooltipster-sidetip .tooltipster-box .tooltipster-content .meta .quality,#slider .item .info .actions .watchnow:hover,#slider .item .info .meta .quality,#episodes #servers .server.active > div,.watch-extra section.info .info .meta .quality{color:#fff}header #menu > li > a:hover{color:#fff}.tooltipster-sidetip .tooltipster-box .tooltipster-content .actions .bookmark{background:#333940}.tooltipster-sidetip .tooltipster-box .tooltipster-content .actions .bookmark:hover{background:#2c3237}.heading .more{color:#c3cfc3}
<?php } ?>
<?php if ($color_style == 'light') { ?>
body {
  color: #555;
  background-color: #fafafa !important;
}

#slider h3,
.filmlist .item .meta .type,
header #menu > li > a {
  color: #fff !important;
}
#slider .item .info .meta .quality {
  background: #fff;
}
.filmlist .item .title,
.watch-extra section.info .info .title,
.watch-extra section.info .info a,
h2,
h3,
.nav .breadcrumb a,
.nav .breadcrumb {
  color: #555 !important;
}
.filmlist .item .icons > div {
  box-shadow: none;
}
h1.site-title,
p.site-title {
  background: #144184 !important;
  color: #fff !important;
}
#watch {
  background-color: #fafafa !important;
}
.nav .breadcrumb {
  display: none;
}
#body {
  background: #fafafa !important;
}
#watch #controls .items > div,
#watch #controls .items > span {
  border: 1px solid #eee;
  color: #000;
  background: #eee;
}
#watch #controls .items > div:hover,
#watch #controls .items > span:hover {
  color: #fff;
  border-color: var(--primary);
  background-color: var(--primary);
}
#episodes #servers .server {
  background: #eee;
  color: #000;
}
#episodes #servers .server:before,
#episodes #servers .server > span,
#episodes #servers .server > div,
.tooltipster-sidetip .tooltipster-box .tooltipster-content .title,
.tooltipster-sidetip .tooltipster-box .tooltipster-content .desc,
.tooltipster-sidetip .tooltipster-box .tooltipster-content .meta,
.tooltipster-sidetip .tooltipster-box .tooltipster-content .meta > div span,
.tooltipster-sidetip .tooltipster-box .tooltipster-content .meta > div a,
.tooltipster-sidetip .tooltipster-box .tooltipster-content .meta > div a:hover,
.tooltipster-sidetip .tooltipster-box .tooltipster-content .meta .imdb {
  color: #000 !important;
}
section.bl .heading .tabs > span {
  color: #000;
  background: #f5f6f7;
}
#slider .item:after,
#watch .play {
  border-bottom: 5px solid var(--primary);
}
section.bl .heading .tabs > span:hover {
  color: #fff;
  background: var(--primary);
}
#slider .item .info .actions a {
  color: #fff !important;
  border: 2px solid #fff !important;
  background-color: var(--primary) !important;
}
header {
  background: #144184 !important;
  color: #fff !important;
  border-bottom: 3px solid #fff;
}
header #search input:focus,
header #search:hover input {
  box-shadow: none;
}
.tooltipster-sidetip .tooltipster-box {
  background: #fafafa !important;
  border: none;
  border-radius: 6px;
}
.tooltipster-sidetip .tooltipster-box .tooltipster-content .meta .quality {
  color: #fff !important;
}
.container .filmlist .item .meta .type {
  background: none;
  color: #666 !important;
  border: 1px solid #959595;
}
.tooltipster-sidetip .tooltipster-box {
  border: 4px solid var(--primary) !important;
}
.tooltipster-sidetip .tooltipster-box .tooltipster-content .actions .watchnow {
  background: #eaebed;
  color: #000;
}
.tooltipster-sidetip
  .tooltipster-box
  .tooltipster-content
  .actions
  .watchnow:hover {
  background: var(--primary);
  color: #fff;
  opacity: 0.9;
}
.tooltipster-sidetip
  .tooltipster-box
  .tooltipster-content
  .actions
  .watchnow
  i {
  color: #000;
}
.tooltipster-sidetip
  .tooltipster-box
  .tooltipster-content
  .actions
  .watchnow:hover
  i {
  color: #fff;
}
.filmlist .item .title {
  font-weight: 500;
}
#slider .item .info .meta .quality {
  color: #666;
}
footer {
  border-top: 5px solid var(--primary);
}

.slide-read-more-button {
  background: #f5f6f7;
}
.shorting {
  color: #555;
}
.badge-secondary {
  background-color: #3e8afa;
}
.filmlist .item .poster {
  background: #f5f6f7;
}
#slider {
  background: #000;
}

#watch > div.play {
  background-color: #000 !important;
}
#commentform textarea,
#commentform #author,
#commentform #email,
#commentform #url {
  background: #fafafa;
  color: #000;
}
.watch-extra section.info .info .rating .stars span {
  color: #eee;
}
.watch-extra section.info .info .rating .stars span i {
  color: #ffa600;
}
.watch-extra section.info .info {
  color: #555;
}
.watch-extra section.info {
  padding: 30px;
  background: #fff;
  border-radius: 5px;
  box-shadow: 0 10px 20px rgb(0 0 0 / 5%);
  min-height: 410px;
}
#episodes #servers,
.bl-2,
#comments {
  padding: 30px;
  background: #fff;
  border-radius: 5px;
  box-shadow: 0 10px 20px rgb(0 0 0 / 5%);
}
#episodes #servers .server:hover {
  background: var(--primary);
}

#episodes #servers .server.active:before:hover {
  color: #fff !important;
}

#episodes #servers .server.active > span {
  color: #fff !important;
}

#episodes #servers .server.active > div {
  color: #fff !important;
}
.modal-content .modal-header .modal-title,
#report-video .form-group .name,
#report-video .form-group > label,
#report-video > div > div > div.modal-header > button {
  color: #555;
}
.modal-content {
  background: #fff;
  color: #555;
}
.custom-select {
  color: #555;
  background: #fff !important;
  border: 1px solid #555;
}
.linea:before {
  border-bottom: 1px solid #ededed;
}
section.bl .heading .title:before,
section.bl .heading h1:before,
section.bl .heading h2:before,
section.bl .heading h3:before {
  background: #fafafa;
}
.info > div.meta.lg > span.imdb > i {
  color: var(--primary);
}
.watch-extra section.info .info .meta .imdb {
  font-weight: 400;
}
.watch-extra section.info .info .meta > div > span:first-child {
  font-weight: 400;
  color: #000000;
}
.watch-extra section.info .info a:hover {
  color: var(--primary);
}
.filmlist .item .poster {
  border-radius: 10px;
  background: #f5f6f7;
}
.filmlist .item .title:hover {
  color: var(--primary) !important;
}
#comments .title-line {
  border-top: 1px solid #ededed;
}
h4.comment-author,
h4.comment-author a,
#comments div.comment-body > em > i,
#comments > h4 {
  color: #555;
}

.filters .filter button {
  background: #eee;

  color: #000;
}
.filters .filter button:hover {
  background: var(--primary);
  color: #fff;
}
.pagenav ul li a,
.pagenav ul li span {
  background: #eee;
  color: #000;
}
.pagenav ul li a:hover,
.pagenav ul li span:hover {
  background: var(--primary);
  color: #000;
}
<?php } ?>
<?php if ($color_style == 'purple') { ?>
body{font-family:Poppins,Arial}#episodes #servers .server.active > span{color:#000}#episodes .episodes .episode a.active,.tooltipster-sidetip .tooltipster-box .tooltipster-content .meta .quality,#slider .item .info .actions .watchnow:hover,#slider .item .info .meta .quality,#episodes #servers .server.active > div,.watch-extra section.info .info .meta .quality{color:#fff}header #menu > li > a:hover{color:#fff}.tooltipster-sidetip .tooltipster-box .tooltipster-content .actions .bookmark{background:#00a247}.tooltipster-sidetip .tooltipster-box .tooltipster-content .actions .bookmark:hover{background:#00a247}.heading .more{color:#c3cfc3}#watch #controls .items > span.bookmark.active:before{color:#00a247}.watch-extra section.info .info .rating .stars span i{color:#bf9900}h1.site-title,p.site-title{background:none;font-family:Poppins,Arial;font-weight:600;text-transform:lowercase}#masthead .container .site-title{position:relative}#masthead .container:before{position:absolute;content:'';display:inline-block;width:40px;height:40px;-moz-border-radius:50%;-webkit-border-radius:50%;border-radius:50%;background-color:#5a2e98}@media screen and (max-width: 1279px){#masthead .container:before{margin-left:45px}}.tooltipster-sidetip .tooltipster-box .tooltipster-content .meta > div a{color:#b082e6}.filmlist .item .meta{color:#959595}.tooltipster-sidetip .tooltipster-box .tooltipster-content .actions .watchnow{background:var(--primary);color:#fff}.tooltipster-sidetip .tooltipster-box .tooltipster-content .actions .watchnow:hover{background:var(--primary);opacity:.9}.tooltipster-sidetip .tooltipster-box .tooltipster-content .actions .watchnow i{margin-right:3px;color:#fff}.tooltipster-sidetip .tooltipster-box .tooltipster-content .actions .watchnow i::before{content:"\f144"}.tooltipster-sidetip .tooltipster-box .tooltipster-content .meta .imdb i{color:#bf9900}.filmlist .item .meta .type{background:none}.heading .more{color:var(--primary)}#colophon .about .logo_txt{background:none;font-family:Poppins,Arial;font-weight:600;text-transform:lowercase;position:relative}#colophon .about:before{position:absolute;content:'';display:inline-block;width:35px;height:35px;-moz-border-radius:50%;-webkit-border-radius:50%;border-radius:50%;background-color:#5a2e98}#colophon > div > div > div.about > div.desc > p.small.font-italic{color:#666}header #menu > li > ul > li > a{color:#6c6c6c}header #menu > li > ul > li > a:hover{color:#fff}.filters .filter button.btn-primary{color:#d1d1d1}.filters .filter button.btn-primary:hover{color:#fff}#masthead.wp-custom-logo .container:before, body.wp-custom-logo #colophon .about:before{display:none}
<?php } ?>
</style>
<?php } ?>
<?php } 