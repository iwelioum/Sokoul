<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
*
* @since 1.0.0
*
*/
// Options
$focode = get_option('_zetaflix_footer_code');
$footer = zeta_get_option('footer', 'complete');
$fotext = zeta_get_option('footertext');
$social1 = zeta_get_option('footersocialfb');
$social2 = zeta_get_option('footersocialtw');
$social3 = zeta_get_option('footersocialig');
$social4 = zeta_get_option('footersocialyt');
$foclm1 = zeta_get_option('footerc1');
$foclm2 = zeta_get_option('footerc2');
$foclm3 = zeta_get_option('footerc3');
$focopy = zeta_get_option('footercopyright');
$fologo = zeta_compose_image_option('logofooter');
$fologo = !empty($fologo) ? $fologo : ZETA_URI.'/assets/img/logo.png';
$fotags = zeta_get_option('footertags');

// Copyright
$copytext = sprintf( __z('%s %s by %s. All Rights Reserved. Theme Design by %s'), '&copy;', date('Y'), '<strong>'.get_option('blogname').'</strong>', '<a href="'.ZETA_SERVER.'/themes/'.ZETA_THEME_SLUG.'" target="_blank" rel="noreferrer nofollow noopener"><strong>StreamFlix</strong></a>' );
$copyright = isset($focopy) ? str_replace('{year}', date('Y'), $focopy) : $copytext;
?>
<div class="clearfix"></div>
    </div>
	
  </div>


  <footer>
  <div class="wrapper">
    <div class="footer">
    <?php if( $footer == 'complete' ) { ?>
      <div class="footer-row">
        <div class="footer-col xtr">
          <div class="footer-xtr">
            <div class="footer-logo">
					<?php
					echo '<div class="logo"><img src="'. $fologo .'" alt="'.get_option('blogname').'" /></div>';
					echo ( $fotext ) ? '<div class="text"><p>'. $fotext. '</p></div>' : null;
					?>
			</div>
            <div class="footer-socials">
              <?php echo ($social1) ? '<a href="'.esc_url($social1).'"><span class="socials-icon"><i class="fa-brands fa-facebook-f"></i></span></a>' : null; ?>
               <?php echo ($social2) ? '<a href="'.esc_url($social2).'"><span class="socials-icon"><i class="fa-brands fa-twitter"></i></a>' : null; ?>
               <?php echo ($social3) ? '<a href="'.esc_url($social3).'"><span class="socials-icon"><i class="fa-brands fa-instagram"></i></span></a>' : null; ?>
              <?php echo ($social4) ? ' <a href="'.esc_url($social4).'"><span class="socials-icon"><i class="fa-brands fa-youtube"></i></span></a>' : null; ?>
            </div>
          </div>
        </div>
        <div class="footer-col links">
          <div class="footer-links">
					   <?php echo ( $foclm1 ) ? '<h3  class="footer-head">'. $foclm1. '</h3>' : null; ?>
					   <?php wp_nav_menu(array('theme_location'=>'footer1','menu_class'=>'footer-links-list', 'container'=>false, 'fallback_cb'=>false)); ?>
          </div>
          <div class="footer-links">
					   <?php echo ( $foclm1 ) ? '<h3  class="footer-head">'. $foclm2. '</h3>' : null; ?>
					   <?php wp_nav_menu(array('theme_location'=>'footer2','menu_class'=>'footer-links-list', 'container'=>false, 'fallback_cb'=>false)); ?>
          </div>
          <div class="footer-links">
					   <?php echo ( $foclm3 ) ? '<h3  class="footer-head">'. $foclm3. '</h3>' : null; ?>
					   <?php wp_nav_menu(array('theme_location'=>'footer3','menu_class'=>'footer-links-list', 'container'=>false, 'fallback_cb'=>false)); ?>
          </div>
        </div>


      </div>
	  <?php if (!empty($fotags)){?>
      <div class="footer-tags">
	  <?php foreach($fotags as $tags){?>
        <a href="<?php echo esc_url($tags['tagurl']);?>"><?php echo esc_html($tags['tagname']);?></a>
	  <?php }?>
     
      </div>
	  <?php }?>
	  <?php $menu_location = get_nav_menu_locations();?>
      <div class="footer-underlinks">    
        <?php  wp_nav_menu( array('theme_location' => 'footer','container_class' => 'underlinks-right', 'menu_class' => 'underlinks-list', 'fallback_cb' => null ) ); ?>      
        <div class="<?php echo (isset($menu_location['footer']) && wp_get_nav_menu_items($menu_location['footer'])) ? 'underlinks-left' : null;?>"> 
          <span class="copyright-btm">
          <?php echo $copyright; ?>
          </span> 
        </div>
      </div>
      <?php } else {?>
        <div class="footer-underlinks">    
        <?php wp_nav_menu( array('theme_location' => 'footer','container_class' => 'underlinks-right', 'menu_class' => 'underlinks-list', 'fallback_cb' => null ) ); ?> 
        <div> 
          <span class="copyright-btm">
          <?php echo $copyright; ?>
          </span> 
        </div>
      </div>
      <?php }?>
    </div>
    
    <div class="clearfix"></div>
  </div>
</footer>

<div id="zeta-modal">
<?php if(!is_user_logged_in()){?>
<div id="guest-modal" class="modalBox animation-3">
  <div class="modal-wrapper">
  <div class="modal-head">
	<span class="modal-title"></span>
	<span class="modal-close"><a class="close-mod"></a></span>
  </div>
  <div class="modal-body">
	<div class="modal-login">
	<?php if(!isset($logg)) ZetaAuth::LoginForm(); ?>
	</div>
  </div>
  </div>
</div>
<?php }?>
<div id="user-modal" class="modalBox animation-3">
  <div class="modal-wrapper">

      <div class="modal-head">
        <span class="modal-title"></span>
        <span class="modal-close"><a class="close-mod"></a></span>
      </div>
      <div class="modal-body">
		<div class="modal-trailer">
		<?php get_template_part('inc/parts/modal_trailer');?>
		</div>
        <div class="modal-report">
          <?php get_template_part('inc/parts/modal_report');?>
        </div>
        <div class="modal-share">
		<?php get_template_part('inc/parts/modal_share');?>
        </div>
      </div>

  </div>
</div>
</div>

<div class="modal-backdrop"></div>
<?php wp_footer();?>
</body>
</html>