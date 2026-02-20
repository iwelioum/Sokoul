<?php
/*
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/

class ZT_Widget_social extends WP_Widget{

	public $sites = array(
		'facebook' => array(
			'title' => 'Facebook',
			'icon'  => 'facebook',
			'fa'    => 'fab fa-facebook-f'
		),
		'twitter' => array(
			'title' => 'Twitter',
			'icon'  => 'twitter',
			'fa'    => 'fab fa-twitter'
		),
		'linkedin' => array(
			'title' => 'Linkedin',
			'icon'  => 'linkedin',
			'fa'    => 'fab fa-linkedin-in'
		),
		'youtube' => array(
			'title' => 'Youtube',
			'icon'  => 'youtube',
			'fa'    => 'fab fa-youtube'
		),
		'rss' => array(
			'title' => 'RSS',
			'icon'  => 'rss',
			'fa'    => 'fas fa-rss'
		),
		'flickr' => array(
			'title' => 'Flickr',
			'icon'  => 'flickr',
			'fa'    => 'fab fa-flickr'
		),
		'vimeo' => array(
			'title' => 'Vimeo',
			'icon'  => 'vimeo',
			'fa'    => 'fab fa-vimeo-v'
		),
		'pinterest' => array(
			'title' => 'Pinterest',
			'icon'  => 'pinterest',
			'fa'    => 'fab fa-pinterest-p'
		),
		'dribbble' => array(
			'title' => 'Dribbble',
			'icon'  => 'dribbble',
			'fa'    => 'fab fa-dribbble'
		),
		'tumblr' => array(
			'title' => 'Tumblr',
			'icon'  => 'tumblr',
			'fa'    => 'fab fa-tumblr'
		),
		'instagram' => array(
			'title' => 'Instagram',
			'icon'  => 'instagram',
			'fa'    => 'fab fa-instagram'
		),
		'VK' => array(
			'title' => 'VK',
			'icon'  => 'vk',
			'fa'    => 'fab fa-vk'
		)
    );

	function __construct(){

		parent::__construct('ztw_socialbox', __z('Zetaflix - Socialbox'), array('classname' => 'ztw_socialbox clearfix', 'description' => __z('Displays links to social networks in a stylish manner')));
	}

	function form($instance){

		$instance = wp_parse_args((array)$instance, array('title' => ''));

		$title =  zeta_isset($instance,'title') ;

		?>
		<p>
			<label for="<?php echo esc_attr($this->get_field_id('title')); ?>"><?php _z('Title:','zetathemes'); ?></label><br>
			<input type="text" class="widefat" id="<?php echo esc_attr($this->get_field_id('title')); ?>" name="<?php echo esc_attr($this->get_field_name('title')); ?>" value="<?php echo esc_attr($title); ?>">
		</p>
		<hr>
		<p><?php _z('Insert the URLs to your social networks'); ?></p><?php

		foreach ($this->sites as $key => $value) {
			echo '<p>';
				echo '<label for="'.esc_attr($this->get_field_id($key)).'">'.esc_attr($value['title']).'</label><br>';
				echo '<input class="widefat" type="text" id="'.esc_attr($this->get_field_id($key)).'" name="'.esc_attr($this->get_field_name($key)).'" value="'.(empty($instance[$key]) ? '' : esc_attr($instance[$key])).'">';
			echo '</p>';
		}
	}
	function update($new_instance, $old_instance){
		$instance = $old_instance;
		$instance['title'] = strip_tags(zeta_isset($new_instance,'title'));
		foreach ($this->sites as $key => $value) {
			$instance[$key] = esc_url($new_instance[$key]);
		}
		return $instance;
	}
	function widget($args, $instance){
		extract($args, EXTR_SKIP);
		echo $before_widget;
		echo !empty($instance['title']) ? $before_title.esc_attr($instance['title']).$after_title : '' ?>
		<div class="widget-social">
			<ul class="social-links">
			<?php foreach ($this->sites as $key => $value) {
					if(!empty($instance[$key])){ ?>
						<li class="dtl">
							<a class="<?php echo esc_attr($value['icon']); ?>-background icls" target="_blank" href="<?php echo esc_url($instance[$key]); ?>">
							<i class="<?php echo esc_attr($value['fa']); ?>"></i> <?php echo esc_attr($value['title']); ?></a>
						</li><?php
					}
				} ?>
			</ul>
		</div><?php

		echo $after_widget;
	}
}
