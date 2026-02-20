<?php
/**
 * @since 1.0.0
 * @version 2.0
 */

CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('Watching'),
        'parent' => 'settings',
        'icon'   => 'fa fa-minus',
        'fields' => array(
            array(
                'id'    => 'watch_location',
                'type'  => 'radio',
                'title' => __z('Watch On'),
                'options' => array(
                    'same' => __z('Same Page'),
                    'separate'  => __z('Separate Page')
                ),
                'default' => 'same'
            ),
			
            array(
                'id'         => 'fakebackdrop',
                'type'       => 'text',
                'title'      => __z('Backdrop URL'),
                'subtitle'      => __z('Show background image by default if the system did not find an image in the content'),
                'dependency' => array('splashscreen', '==', true),
                'attributes' => array(
                    'placeholder' => 'https://'
                )
            ),

			
			array(
                'type'    => 'subheading',
                'content' => __z('Similar Contents')
            ),
              array(
                'id'      => 'similar_module',
                'type'    => 'switcher',
                'title'   => __z('Similar Titles'),
                'subtitle'   => __z('Display related content modules.'),
				'default' => true
            ),
           array(
                'id' => 'similar_style',
                'type' => 'radio',
                'title' => __z('Similar Poster'),
                'subtitle' => __z('Poster style for similar module.'),
                'default' => 'vertical',
                'options' => array(
                    'inherit'    => __z('Inherit'),
                    'vertical' => __z('Vertical'),
                ),
            ),
           array(
                'id' => 'similar_source',
                'type' => 'radio',
                'title' => __z('Similar Source'),
                'subtitle' => __z('Poster image source for similar module.'),
                'default' => 'inherit',
                'options' => array(
                    'inherit'    => __z('Inherit'),
                    'featured' => __z('Featured Image'),
					'poster' => __('Poster Metafield')
                ),
            ),
			
            array(
                'type'       => 'content',
                'content'    => '<h2>'.__z('TV Show Settings').'</h2>',
            ),
			
          array(
                'id' => 'epliststyle',
                'type' => 'radio',
                'title' => __z('Episode List'),
                'subtitle' => __z('Customize how the list look.'),
                'options' => array(
                    'comp' => __z('Complete'),
                    'simp'    => __z('Simple')
                ),
                'default' => 'comp',
            ),
			
            array(
                'id'      => 'playajaxep',
                'type'    => 'switcher',
                'title'   => __z('Ajax Mode'),
				'subtitle' => __z('Load episodes on same page'),
                'default' => true,
                'label'   => __z('This function delivers data safely and agile with WP-JSON')
            ),
			
            array(
                'id'      => 'playautoloadep',
                'type'    => 'switcher',
                'title'   => __z('Auto Load'),
                'default' => false,
                'label'   => __z('Load the first video available of the loaded episode'),
				 'dependency' => array('playajaxep', '==', true)
            ),
			
            array(
                'id'       => 'ajaxepdisplay',
                'type'     => 'checkbox',
                'title'    => __z('Use On'),
				'subtitle' => __z('Select which page to use ajax episodes'),
                'options'  => array(
                    'tvshows' => __z('TV Shows'),
                    'seasons' => __z('TV Shows > Seasons'),
					'episodes' => __z('TV Shows > Episodes'),
                ),
                'default'  => array('tvshows','seasons'),
				 'dependency' => array('playajaxep', '==', true)
            ),

            array(
                'id' => 'playajaxmethodep',
                'type' => 'radio',
                'title' => __z('Delivery method'),
                'subtitle' => __z('Select the most convenient delivery method for your website.'),
                'default' => 'admin_ajax',
                'options' => array(
                    'admin_ajax' => '<code><strong>admin-ajax</strong></code> '.__z('This method is safe but not very agile'),
                    'wp_json'    => '<code><strong>wp-json</strong></code> '.__z('This method is simplified and very agile.')
                ),
                'dependency' => array('playajaxep', '==', true)
            ),
            array(
                'type'    => 'notice',
                'style'   => 'info',
                'content' => __z('If you have important traffic it would be advisable not to activate this function, if it is activated we recommend deactivating the Auto Load'),
                'dependency' => array('playajaxep|playajaxmethodep','==|','true|admin_ajax')
            ),
            array(
                'type'    => 'notice',
                'style'   => 'info',
                'content' => __z('The selected delivery method is unsafe but very agile, if you have significant traffic we recommend disabling automatic loading'),
                'dependency' => array('playajaxep|playajaxmethodep','==|','true|wp_json')
            ),
			

			
			 array(
                'type'    => 'subheading',
                'content' => __z('Seasons Data')
            ),
			
           array(
                'id' => 'tvssdata',
                'type' => 'radio',
                'title' => __z('Data Source'),
                'subtitle' => __z('Select which data to display on season page.'),
                'options' => array(
                    'inherit'    => __z('Inherit from TV Show'),
                    'default' => __z('Default post data'),
                ),
                'default' => 'inherit',
            ),	
			
           array(
                'id' => 'tvssposter',
                'type' => 'radio',
                'title' => __z('Poster Source'),
                'subtitle' => __z('Select which poster image to display on season page.'),
                'options' => array(
                    'inherit'    => __z('Inherit from TV Show'),
                    'default' => __z('Default post image'),
                ),
                'default' => 'inherit',
            ),
			
			 array(
                'type'    => 'subheading',
                'content' => __z('Episodes Data')
            ),
			
           array(
                'id' => 'tvepdata',
                'type' => 'radio',
                'title' => __z('Data Source'),
                'subtitle' => __z('Select which data to display on season page.'),
                'options' => array(
                    'inherit'    => __z('Inherit from TV Show'),
                    'default' => __z('Default post data'),
                ),
                'default' => 'default',
            ),	
			
           array(
                'id' => 'tvepimages',
                'type' => 'radio',
                'title' => __z('Poster Source'),
                'subtitle' => __z('Select which poster image to display on episode page.'),
                'options' => array(
					'inherittv'    => __z('Inherit from TV Show'),
                    'inheritss'    => __z('Inherit from Season'),
                    'default' => __z('Default post image'),
                ),
                'default' => 'inheritss',
            ),				
			
        )
    )
);
